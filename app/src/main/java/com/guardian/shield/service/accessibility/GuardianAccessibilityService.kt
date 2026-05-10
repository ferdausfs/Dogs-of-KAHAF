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
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.service.blocker.BlockingEngine
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.RulesEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * FIX-LOG (vs original):
 *  - BUG #2: AI scan only ran on text-change events → image-only apps (Reels, Gallery,
 *            video players) never triggered detection. Now a periodic scanner runs every
 *            `AI_PERIODIC_MS` (default 1500 ms) for the active foreground package as long
 *            as it is blockable AND ai_detection toggle is on.
 *  - BUG #10: lastAiScanMs was global → first 3 s after switching apps was a blind spot.
 *            Throttle is now per-package.
 *  - BUG #14: ensure only one screenshot+inference is in flight at a time (atomic flag).
 *  - notificationTimeout reduced reliance on aggressive event delivery; periodic scan
 *    decouples blocking from text events entirely.
 */
@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    companion object {
        private const val TEXT_THROTTLE_MS = 600L
        private const val AI_THROTTLE_MS   = 1_500L  // per-package
        private const val AI_PERIODIC_MS   = 1_500L  // periodic scan cadence
    }

    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var blockingEngine: BlockingEngine
    @Inject lateinit var aiDetector: AiDetector
    @Inject lateinit var prefs: GuardianPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var periodicJob: Job? = null

    @Volatile private var lastForegroundPkg: String? = null
    private var lastTextScanMs = 0L
    // BUG #10 fix — per-package throttle.
    private val lastAiScanByPkg = HashMap<String, Long>()
    // BUG #14 fix — guard against concurrent screenshot+inference.
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
        Timber.i("GuardianAccessibilityService connected")
        scope.launch { rulesEngine.reload() }
        LocalBroadcastManager.getInstance(this).registerReceiver(
            rulesReloadReceiver, IntentFilter(RulesEngine.ACTION_RULES_CHANGED)
        )
        startPeriodicAiScanner()  // BUG #2 fix
    }

    /**
     * BUG #2 fix: a coroutine that ticks every AI_PERIODIC_MS and runs an AI screenshot
     * scan on the current foreground package, regardless of whether any accessibility
     * text event has fired. This is what actually catches image-heavy apps.
     */
    private fun startPeriodicAiScanner() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                delay(AI_PERIODIC_MS)
                val pkg = lastForegroundPkg ?: continue
                if (!rulesEngine.canBlock(pkg)) continue
                val enabled = runCatching { prefs.aiDetectionEnabled.first() }.getOrDefault(false)
                if (!enabled) continue
                if (!aiDetector.isModelAvailable()) continue
                triggerAiCheck(pkg)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED  -> handleWindowChange(pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED     -> handleContentChange(pkg, event)
        }
    }

    private fun handleWindowChange(pkg: String) {
        if (pkg == lastForegroundPkg) return
        lastForegroundPkg = pkg

        when (val result = rulesEngine.evaluatePackage(pkg)) {
            is DetectionResult.Block -> blockingEngine.block(pkg, result.reason, result.detail)
            DetectionResult.Allow -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && rulesEngine.canBlock(pkg)) {
                    triggerAiCheck(pkg)
                }
            }
        }
    }

    private fun handleContentChange(pkg: String, event: AccessibilityEvent) {
        if (!rulesEngine.canBlock(pkg)) return

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
                    DetectionResult.Allow -> { /* periodic scanner handles AI */ }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun triggerAiCheck(pkg: String) {
        // BUG #10 — per-package throttle.
        val now = System.currentTimeMillis()
        val last = lastAiScanByPkg[pkg] ?: 0L
        if (now - last < AI_THROTTLE_MS) return
        lastAiScanByPkg[pkg] = now

        // BUG #14 — only one inference in flight at a time.
        if (!aiInFlight.compareAndSet(false, true)) return

        scope.launch {
            try {
                val aiEnabled = prefs.aiDetectionEnabled.first()
                if (!aiEnabled) { aiInFlight.set(false); return@launch }
                if (!aiDetector.ensureLoaded()) {
                    Timber.w("AI model not loaded — skipping screenshot check")
                    aiInFlight.set(false); return@launch
                }
                if (rulesEngine.isWhitelisted(pkg)) { aiInFlight.set(false); return@launch }

                withContext(Dispatchers.Main) {
                    takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(result: ScreenshotResult) {
                                scope.launch(Dispatchers.Default) {
                                    var bmp: Bitmap? = null
                                    try {
                                        bmp = Bitmap.wrapHardwareBuffer(
                                            result.hardwareBuffer, result.colorSpace
                                        )?.copy(Bitmap.Config.ARGB_8888, false)
                                        result.hardwareBuffer.close()

                                        if (bmp == null) return@launch

                                        if (aiDetector.isUnsafe(bmp)) {
                                            Timber.d("AI flagged content in $pkg")
                                            withContext(Dispatchers.Main) {
                                                blockingEngine.block(
                                                    pkg, BlockReason.AI_DETECTION,
                                                    "AI detected unsafe content"
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "AI screenshot processing error")
                                    } finally {
                                        bmp?.recycle()
                                        aiInFlight.set(false)
                                    }
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                Timber.w("takeScreenshot failed: errorCode=$errorCode")
                                aiInFlight.set(false)
                            }
                        }
                    )
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
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            if (node !== root) node.recycle()
            nodes++
        }
        queue.forEach { if (it !== root) it.recycle() }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        periodicJob?.cancel()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(rulesReloadReceiver)
        scope.cancel()
        aiDetector.close()
        super.onDestroy()
    }
}
