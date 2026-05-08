package com.guardian.shield.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_NONE
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.service.blocker.BlockingEngine
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.RulesEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * v8 FIX-LOG (stability pass):
 *  • BUG-01 → triggerAiCheck(): wrap the entire withContext(Main) screenshot
 *    block in a try/finally so aiInFlight ALWAYS resets, even if takeScreenshot
 *    throws synchronously or the coroutine is cancelled before the callback
 *    is registered.
 *  • BUG-02 → startPeriodicAiScanner(): each tick is wrapped in try/catch so
 *    a single failed iteration never kills the loop. delay() is the only
 *    cancellation point that's allowed to break the loop.
 *  • BUG-03 → lastAiScanByPkg is now capped at MAX_AI_SCAN_MAP entries; the
 *    oldest entry is evicted before insert when the cap would be exceeded.
 *  • BUG-09 → On API < 30, schedule a chain of 3 follow-up scans (best-effort
 *    fallback for scroll-heavy apps where periodic screenshot scanning isn't
 *    available without MediaProjection).
 */
@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    companion object {
        private const val TEXT_THROTTLE_MS = 600L
        private const val AI_THROTTLE_MS = 700L
        private const val AI_PERIODIC_MS = 850L
        private const val AI_FOLLOW_UP_MS = 450L

        // BUG-03: bound the per-package throttle map.
        private const val MAX_AI_SCAN_MAP = 50

        // BUG-05: companion flag the foreground watchdog can poll. Settings
        // can claim accessibility is "enabled" while the OS has actually
        // killed the bound service on aggressive OEMs.
        @Volatile var isRunning: Boolean = false
            private set
    }

    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var blockingEngine: BlockingEngine
    @Inject lateinit var aiDetector: AiDetector
    @Inject lateinit var prefs: GuardianPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var periodicJob: Job? = null

    @Volatile private var lastForegroundPkg: String? = null
    private var lastTextScanMs = 0L
    private val lastAiScanByPkg = HashMap<String, Long>()
    private val aiInFlight = AtomicBoolean(false)

    private val rulesReloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            scope.launch {
                rulesEngine.reload()
                Timber.d("RulesEngine reloaded via broadcast")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Timber.i("GuardianAccessibilityService connected")

        scope.launch {
            rulesEngine.reload()
            // Try to load both legacy + new pipelines. Failures are silent.
            runCatching {
                if (aiDetector.isModelAvailable()) aiDetector.ensureLoaded()
            }.onFailure { Timber.e(it, "Legacy model preload failed") }
            runCatching {
                if (aiDetector.isNsfwModelAvailable()) aiDetector.ensureGenderPipelineLoaded()
            }.onFailure { Timber.e(it, "Gender pipeline preload failed") }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            rulesReloadReceiver,
            IntentFilter(RulesEngine.ACTION_RULES_CHANGED)
        )
        startPeriodicAiScanner()
    }

    private fun startPeriodicAiScanner() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                delay(AI_PERIODIC_MS)
                // BUG-02: single tick failures must NOT kill the loop.
                try {
                    val pkg = lastForegroundPkg ?: continue
                    if (!rulesEngine.canBlock(pkg)) continue
                    val enabled = runCatching { prefs.aiDetectionEnabled.first() }.getOrDefault(false)
                    if (!enabled) continue
                    if (!aiDetector.isModelAvailable() && !aiDetector.isNsfwModelAvailable()) continue
                    triggerAiCheck(pkg)
                } catch (t: Throwable) {
                    Timber.w(t, "Periodic AI tick failed; loop continues")
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowChange(pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleContentChange(pkg, event)
        }
    }

    private fun handleWindowChange(pkg: String) {
        if (pkg == lastForegroundPkg) return
        lastForegroundPkg = pkg

        when (val result = rulesEngine.evaluatePackage(pkg)) {
            is DetectionResult.Block -> {
                blockingEngine.block(pkg, result.reason, result.detail)
            }

            DetectionResult.Allow -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && rulesEngine.canBlock(pkg)) {
                    triggerAiCheck(pkg, force = true)
                    scheduleFollowUpScan(pkg)
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && rulesEngine.canBlock(pkg)) {
                    // BUG-09: API 26-29 has no screenshot API; we run a chain of
                    // text-only follow-up scans so scroll-heavy apps still get
                    // multiple chances to flag content. Best-effort only.
                    scheduleLegacyFollowUpChain(pkg)
                }
            }
        }
    }

    private fun scheduleFollowUpScan(pkg: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        scope.launch {
            delay(AI_FOLLOW_UP_MS)
            if (lastForegroundPkg == pkg) {
                triggerAiCheck(pkg)
            }
        }
    }

    /**
     * BUG-09: best-effort progressive content rescan for API < 30.
     * Without MediaProjection we cannot screenshot, so we re-scan visible
     * text at 500/1500/3000 ms — gives 3 chances to catch progressively
     * loaded content (Instagram, gallery, browser).
     */
    private fun scheduleLegacyFollowUpChain(pkg: String) {
        scope.launch {
            val delays = longArrayOf(500L, 1500L, 3000L)
            for (d in delays) {
                delay(d)
                if (lastForegroundPkg != pkg) return@launch
                if (!rulesEngine.canBlock(pkg)) return@launch
                try {
                    val text = collectVisibleText(rootInActiveWindow)
                    if (!text.isNullOrBlank()) {
                        when (val result = rulesEngine.evaluateText(text)) {
                            is DetectionResult.Block -> withContext(Dispatchers.Main) {
                                blockingEngine.block(pkg, BlockReason.KEYWORD_MATCH, result.detail)
                            }
                            DetectionResult.Allow -> Unit
                        }
                    }
                } catch (t: Throwable) {
                    Timber.w(t, "Legacy follow-up scan failed")
                }
            }
        }
    }

    private fun handleContentChange(pkg: String, event: AccessibilityEvent) {
        if (!rulesEngine.canBlock(pkg)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            triggerAiCheck(pkg)
        }

        val now = System.currentTimeMillis()
        if (now - lastTextScanMs < TEXT_THROTTLE_MS) return
        lastTextScanMs = now

        scope.launch {
            val text = collectVisibleText(rootInActiveWindow)
            if (!text.isNullOrBlank()) {
                when (val result = rulesEngine.evaluateText(text)) {
                    is DetectionResult.Block -> withContext(Dispatchers.Main) {
                        blockingEngine.block(pkg, BlockReason.KEYWORD_MATCH, result.detail)
                    }

                    DetectionResult.Allow -> Unit
                }
            }
        }
    }

    /**
     * BUG-03: bounded per-package throttle map.
     * Removes the oldest entry when the cap would be exceeded.
     */
    private fun recordScanTime(pkg: String, now: Long) {
        synchronized(lastAiScanByPkg) {
            if (lastAiScanByPkg.size >= MAX_AI_SCAN_MAP && !lastAiScanByPkg.containsKey(pkg)) {
                val oldestKey = lastAiScanByPkg.minByOrNull { it.value }?.key
                if (oldestKey != null) lastAiScanByPkg.remove(oldestKey)
            }
            lastAiScanByPkg[pkg] = now
        }
    }

    private fun lastScanTimeFor(pkg: String): Long =
        synchronized(lastAiScanByPkg) { lastAiScanByPkg[pkg] ?: 0L }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun triggerAiCheck(pkg: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val last = lastScanTimeFor(pkg)
        if (!force && now - last < AI_THROTTLE_MS) return
        recordScanTime(pkg, now)

        if (!aiInFlight.compareAndSet(false, true)) return

        scope.launch {
            try {
                val aiEnabled = runCatching { prefs.aiDetectionEnabled.first() }.getOrDefault(false)
                if (!aiEnabled) {
                    aiInFlight.set(false)
                    return@launch
                }

                // Read user gender once per scan (cheap — DataStore).
                val userGender = runCatching { prefs.userGender.first() }.getOrDefault(GENDER_NONE)

                // Decide which detectors are even possible right now.
                val genderFeatureOn = userGender != GENDER_NONE && aiDetector.isNsfwModelAvailable()
                val legacyOn        = aiDetector.isModelAvailable()

                if (!genderFeatureOn && !legacyOn) {
                    aiInFlight.set(false)
                    return@launch
                }

                // Eagerly attempt loads — failures are silent inside AiDetector.
                if (genderFeatureOn) aiDetector.ensureGenderPipelineLoaded()
                if (legacyOn)        aiDetector.ensureLoaded()

                if (!rulesEngine.canBlock(pkg)) {
                    aiInFlight.set(false)
                    return@launch
                }

                // BUG-01: guarantee aiInFlight is reset even if takeScreenshot
                // throws synchronously or the coroutine is cancelled before
                // either callback fires. We use a local flag to know whether
                // a callback successfully claimed ownership; if not, we reset
                // here in finally.
                val callbackTookOver = AtomicBoolean(false)
                try {
                    withContext(Dispatchers.Main) {
                        try {
                            takeScreenshot(
                                Display.DEFAULT_DISPLAY,
                                mainExecutor,
                                object : TakeScreenshotCallback {
                                    override fun onSuccess(result: ScreenshotResult) {
                                        callbackTookOver.set(true)
                                        scope.launch(Dispatchers.Default) {
                                            var bmp: Bitmap? = null
                                            try {
                                                bmp = Bitmap.wrapHardwareBuffer(
                                                    result.hardwareBuffer,
                                                    result.colorSpace
                                                )?.copy(Bitmap.Config.ARGB_8888, false)
                                                runCatching { result.hardwareBuffer.close() }

                                                val safeBmp = bmp ?: return@launch

                                                // ── Step 1: opposite-gender NSFW (if armed) ──
                                                var blocked = false
                                                if (genderFeatureOn) {
                                                    val hit = runCatching {
                                                        aiDetector.isOppositeGenderNsfw(safeBmp, userGender)
                                                    }.onFailure {
                                                        Timber.e(it, "isOppositeGenderNsfw threw")
                                                    }.getOrDefault(false)

                                                    if (hit) {
                                                        Timber.d("AI flagged opposite-gender NSFW in $pkg")
                                                        withContext(Dispatchers.Main) {
                                                            blockingEngine.block(
                                                                pkg,
                                                                BlockReason.AI_DETECTION,
                                                                "AI detected opposite-gender NSFW content"
                                                            )
                                                        }
                                                        blocked = true
                                                    }
                                                }

                                                // ── Step 2: legacy generic NSFW fallback ──
                                                if (!blocked && legacyOn) {
                                                    val hit = runCatching {
                                                        aiDetector.isUnsafe(safeBmp)
                                                    }.onFailure {
                                                        Timber.e(it, "isUnsafe threw")
                                                    }.getOrDefault(false)

                                                    if (hit) {
                                                        Timber.d("AI flagged content in $pkg")
                                                        withContext(Dispatchers.Main) {
                                                            blockingEngine.block(
                                                                pkg,
                                                                BlockReason.AI_DETECTION,
                                                                "AI detected unsafe content"
                                                            )
                                                        }
                                                    }
                                                }
                                            } catch (oom: OutOfMemoryError) {
                                                Timber.e(oom, "OOM in screenshot pipeline")
                                            } catch (e: Exception) {
                                                Timber.e(e, "AI screenshot processing error")
                                            } finally {
                                                runCatching { if (bmp?.isRecycled == false) bmp.recycle() }
                                                aiInFlight.set(false)
                                            }
                                        }
                                    }

                                    override fun onFailure(errorCode: Int) {
                                        callbackTookOver.set(true)
                                        Timber.w("takeScreenshot failed: errorCode=$errorCode")
                                        aiInFlight.set(false)
                                    }
                                }
                            )
                        } catch (t: Throwable) {
                            // Synchronous throw from takeScreenshot itself
                            // (e.g. service detached). Caller's finally will
                            // reset aiInFlight because callbackTookOver is
                            // still false.
                            Timber.w(t, "takeScreenshot threw synchronously")
                            throw t
                        }
                    }
                } finally {
                    // BUG-01 safety net: if neither callback claimed the in-flight
                    // flag (sync throw, cancellation, etc.) we MUST clear it here
                    // or detection silently stops forever.
                    if (!callbackTookOver.get()) {
                        aiInFlight.set(false)
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "triggerAiCheck error")
                aiInFlight.set(false)
            }
        }
    }

    private fun collectVisibleText(root: AccessibilityNodeInfo?): String? {
        root ?: return null
        val sb = StringBuilder()
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.add(root)
        var nodes = 0
        while (queue.isNotEmpty() && nodes < 250) {
            val node = queue.removeFirst()
            node.text?.let { sb.append(it).append(' ') }
            node.contentDescription?.let { sb.append(it).append(' ') }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            if (node !== root) node.recycle()
            nodes++
        }
        queue.forEach { if (it !== root) it.recycle() }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isRunning = false
        runCatching { periodicJob?.cancel() }
        runCatching {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(rulesReloadReceiver)
        }
        runCatching { scope.cancel() }
        runCatching { aiDetector.close() }
        super.onDestroy()
    }
}
