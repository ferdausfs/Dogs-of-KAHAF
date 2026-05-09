package com.kahaf.guardianshield.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.kahaf.guardianshield.R
import com.kahaf.guardianshield.domain.model.BlockReason
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import com.kahaf.guardianshield.domain.usecase.EvaluateForegroundAppUseCase
import com.kahaf.guardianshield.domain.usecase.RecordBlockEventUseCase
import com.kahaf.guardianshield.domain.usecase.ScanTextForKeywordsUseCase
import com.kahaf.guardianshield.service.foreground.GuardianForegroundService
import com.kahaf.guardianshield.service.overlay.BlockOverlayActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Heart of the protection logic.
 *
 *  - Filters tightly to TYPE_WINDOW_STATE_CHANGED + TYPE_VIEW_TEXT_CHANGED +
 *    TYPE_WINDOW_CONTENT_CHANGED to keep wakelock burn minimal.
 *  - Coalesces text scans with a 250ms debounce.
 *  - Wraps every callback in try/catch — never throws.
 *  - Starts the foreground service on connect so the system keeps us alive.
 */
@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    @Inject lateinit var evaluateForegroundApp: EvaluateForegroundAppUseCase
    @Inject lateinit var scanTextForKeywords: ScanTextForKeywordsUseCase
    @Inject lateinit var recordBlockEvent: RecordBlockEventUseCase
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var lastForegroundPackage: String? = null
    @Volatile private var lastBlockedAtMs: Long = 0L
    private var pendingScanJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            instance = this
            // Start FG so the system gives us higher priority and a visible notif.
            GuardianForegroundService.start(applicationContext)
            Log.i(TAG, "Accessibility service connected")
        } catch (t: Throwable) {
            Log.e(TAG, "onServiceConnected error", t)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            // Respect global protection toggle without blocking the dispatch thread.
            scope.launch { handleEvent(event.eventType, event.packageName?.toString()) }
        } catch (t: Throwable) {
            Log.e(TAG, "onAccessibilityEvent error", t)
        }
    }

    private suspend fun handleEvent(eventType: Int, pkg: String?) {
        try {
            val protectionOn = settingsRepository.appSettings.first().protectionEnabled
            if (!protectionOn) return
            val safePkg = pkg?.takeIf { it.isNotBlank() } ?: return
            // Ignore our own package
            if (safePkg == applicationContext.packageName) return

            when (eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    onForegroundChanged(safePkg)
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    scheduleTextScan(safePkg)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "handleEvent error", t)
        }
    }

    private suspend fun onForegroundChanged(pkg: String) {
        if (pkg == lastForegroundPackage &&
            System.currentTimeMillis() - lastBlockedAtMs < 1500L
        ) return
        lastForegroundPackage = pkg
        try {
            val decision = evaluateForegroundApp(pkg)
            if (decision.shouldBlock) {
                triggerBlock(
                    pkg = pkg,
                    reason = decision.reason ?: BlockReason.APP_RULE,
                    detail = decision.detail,
                    lockedUntilMs = decision.lockedUntilMs,
                    reasonRes = reasonResFor(decision.reason)
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Eval error", t)
        }
    }

    /** Coalesce rapid text-changed events into one 250ms debounced scan. */
    private fun scheduleTextScan(pkg: String) {
        pendingScanJob?.cancel()
        pendingScanJob = scope.launch {
            delay(250L)
            try {
                val text = collectVisibleText() ?: return@launch
                if (text.isBlank()) return@launch
                val match = scanTextForKeywords(text) ?: return@launch
                triggerBlock(
                    pkg = pkg,
                    reason = BlockReason.KEYWORD,
                    detail = "Matched: ${match.pattern.take(40)}",
                    lockedUntilMs = 0L,
                    reasonRes = R.string.blk_reason_keyword
                )
            } catch (t: Throwable) {
                Log.e(TAG, "scheduleTextScan error", t)
            }
        }
    }

    private fun collectVisibleText(): String? {
        return try {
            val root: AccessibilityNodeInfo = rootInActiveWindow ?: return null
            val sb = StringBuilder()
            walk(root, sb)
            sb.toString()
        } catch (t: Throwable) {
            Log.e(TAG, "collectVisibleText error", t)
            null
        }
    }

    private fun walk(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int = 0) {
        if (node == null || depth > MAX_DEPTH || sb.length > MAX_TEXT_BYTES) return
        try {
            node.text?.let { if (it.isNotEmpty()) sb.append(it).append(' ') }
            node.contentDescription?.let { if (it.isNotEmpty()) sb.append(it).append(' ') }
            val n = node.childCount
            for (i in 0 until n) walk(node.getChild(i), sb, depth + 1)
        } catch (_: Throwable) {
            // never throw from accessibility walk
        }
    }

    private suspend fun triggerBlock(
        pkg: String,
        reason: BlockReason,
        detail: String,
        lockedUntilMs: Long,
        reasonRes: Int
    ) {
        // Throttle: don't fire overlay more than once per 1.5s for same package.
        val now = System.currentTimeMillis()
        if (now - lastBlockedAtMs < 1500L) return
        lastBlockedAtMs = now
        try {
            recordBlockEvent(pkg, reason, detail)
        } catch (_: Throwable) { /* logging is non-critical */ }
        val intent = BlockOverlayActivity.newIntent(
            context = applicationContext,
            packageName = pkg,
            reasonRes = reasonRes,
            lockedUntilMs = lockedUntilMs
        )
        try {
            applicationContext.startActivity(intent)
            // Belt-and-suspenders: also send the user back home so the offending
            // surface isn't visible behind a slow overlay launch.
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch overlay", t)
        }
    }

    private fun reasonResFor(r: BlockReason?): Int = when (r) {
        BlockReason.APP_RULE -> R.string.blk_reason_app
        BlockReason.KEYWORD -> R.string.blk_reason_keyword
        BlockReason.SCHEDULE -> R.string.blk_reason_schedule
        BlockReason.AI_NSFW -> R.string.blk_reason_ai
        BlockReason.AUTO_LOCK -> R.string.blk_reason_lock
        null -> R.string.blk_reason_app
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        try {
            instance = null
            scope.cancel()
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GuardianA11y"
        private const val MAX_DEPTH = 24
        private const val MAX_TEXT_BYTES = 8192

        @Volatile var instance: GuardianAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null

        fun goHomeNow() = instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
    }
}
