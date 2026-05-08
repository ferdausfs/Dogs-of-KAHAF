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
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_NONE
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.service.blocker.BlockingEngine
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.RulesEngine
import com.guardian.shield.util.GuardianConstants
import com.guardian.shield.util.Scopes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * v9 (2.0.0) FIX-LOG (performance + cleanup pass):
 *  • P1-C → preference reads on every tick are gone. AiDetector.startPrefsCache
 *    is called once from onServiceConnected; tick logic reads cached fields.
 *  • P1-E → screen-state-aware periodic scanner. ACTION_SCREEN_OFF pauses /
 *    slows the scanner to SCREEN_OFF_PERIODIC_MS. ACTION_SCREEN_ON resumes.
 *  • P2-B → LocalBroadcastManager removed. We collect RulesEngine.rulesChanged
 *    SharedFlow directly via the service scope.
 *  • P2-C → collectVisibleText() now tracks every recycled node in a visited
 *    set so we never call recycle() twice on the same handle (some OEMs let
 *    `getChild(i)` return a node that's already a sibling reference).
 *
 * Earlier v8 fixes preserved:
 *   - BUG-01 → triggerAiCheck() always resets aiInFlight via finally.
 *   - BUG-02 → periodic loop wraps each tick in try/catch.
 *   - BUG-03 → bounded per-package throttle map.
 *   - BUG-09 → API < 30 follow-up scan chain.
 *   - companion isRunning flag for the foreground watchdog (BUG-05).
 */
@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    companion object {
        private const val TEXT_THROTTLE_MS = GuardianConstants.TEXT_THROTTLE_MS
        private const val AI_THROTTLE_MS = GuardianConstants.AI_THROTTLE_MS
        private const val AI_PERIODIC_MS = GuardianConstants.AI_PERIODIC_MS
        private const val AI_FOLLOW_UP_MS = GuardianConstants.AI_FOLLOW_UP_MS
        private const val SCREEN_OFF_PERIODIC_MS = GuardianConstants.SCREEN_OFF_PERIODIC_MS
        private const val MAX_AI_SCAN_MAP = GuardianConstants.MAX_AI_SCAN_MAP

        @Volatile var isRunning: Boolean = false
            private set
    }

    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var blockingEngine: BlockingEngine
    @Inject lateinit var aiDetector: AiDetector
    @Inject lateinit var prefs: GuardianPreferences

    private val scope: CoroutineScope = Scopes.default()
    private var periodicJob: Job? = null

    @Volatile private var lastForegroundPkg: String? = null
    private var lastTextScanMs = 0L
    private val lastAiScanByPkg = HashMap<String, Long>()
    private val aiInFlight = AtomicBoolean(false)

    // P1-E: live screen state. Defaults to ON because the service is normally
    // bound while the user is interacting with the device.
    @Volatile private var isScreenOn = true

    // P4-C: cached master protection switch. Refreshed via a hot collector.
    @Volatile private var protectionMasterEnabled = true

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    Timber.d("Screen OFF — AI scanner slowed")
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    Timber.d("Screen ON — AI scanner resumed")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Timber.i("GuardianAccessibilityService connected")

        // P1-C: start hot collectors for the prefs the AI loop reads on every
        // tick. ONE collector per pref instead of a DataStore read per tick.
        runCatching { aiDetector.startPrefsCache(scope) }

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

        // P2-B: collect RulesEngine.rulesChanged directly — replaces the
        // deprecated LocalBroadcastManager + BroadcastReceiver wiring.
        scope.launch {
            runCatching {
                rulesEngine.rulesChanged.collect {
                    Timber.d("RulesEngine reloaded via SharedFlow")
                }
            }.onFailure { Timber.w(it, "rulesChanged collector failed") }
        }

        // P4-C: hot collector for the master protection switch.
        scope.launch {
            runCatching {
                prefs.protectionEnabled.collect { protectionMasterEnabled = it }
            }.onFailure { Timber.w(it, "protectionEnabled collector failed") }
        }

        // P1-E: register screen state receiver.
        runCatching {
            registerReceiver(
                screenStateReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                }
            )
        }.onFailure { Timber.w(it, "Failed to register screen state receiver") }

        startPeriodicAiScanner()
    }

    /**
     * P1-E: screen-state-aware. When the screen is OFF we use a much longer
     * delay (SCREEN_OFF_PERIODIC_MS) AND skip the scan body entirely. This
     * preserves battery while still allowing a quick wake when the screen
     * turns back on.
     */
    private fun startPeriodicAiScanner() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                val interval = if (isScreenOn) AI_PERIODIC_MS else SCREEN_OFF_PERIODIC_MS
                delay(interval)
                if (!isScreenOn) continue   // skip the scan body while screen is off

                // BUG-02: single tick failures must NOT kill the loop.
                try {
                    val pkg = lastForegroundPkg ?: continue
                    if (!rulesEngine.canBlock(pkg)) continue
                    // P1-C: cached pref instead of DataStore read.
                    if (!aiDetector.cachedAiEnabled) continue
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
        // P4-C: master protection switch — skip all processing when paused.
        // We read the cached pref via a fast non-blocking flow.last() emulation
        // by checking the AiDetector pref cache hot-state; the master switch
        // itself uses a synchronous boolean cache.
        if (!protectionMasterEnabled) return
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
                // P1-C: cached pref reads (was prefs.aiDetectionEnabled.first()).
                val aiEnabled = aiDetector.cachedAiEnabled
                if (!aiEnabled) {
                    aiInFlight.set(false)
                    return@launch
                }

                // P1-C: cached gender (was prefs.userGender.first()).
                val userGender = aiDetector.cachedUserGender

                // Decide which detectors are even possible right now.
                val genderFeatureOn = userGender != GENDER_NONE && aiDetector.isNsfwModelAvailable()
                val legacyOn        = aiDetector.isModelAvailable()

                if (!genderFeatureOn && !legacyOn) {
                    aiInFlight.set(false)
                    return@launch
                }

                if (genderFeatureOn) aiDetector.ensureGenderPipelineLoaded()
                if (legacyOn)        aiDetector.ensureLoaded()

                if (!rulesEngine.canBlock(pkg)) {
                    aiInFlight.set(false)
                    return@launch
                }

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
                            Timber.w(t, "takeScreenshot threw synchronously")
                            throw t
                        }
                    }
                } finally {
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

    /**
     * P2-C: visited-set tracking — avoids double-recycle when a child node
     * reference is also reachable through another path on certain OEMs.
     * We collect every non-root node into [toRecycle] and recycle each
     * exactly once after the BFS completes.
     */
    private fun collectVisibleText(root: AccessibilityNodeInfo?): String? {
        root ?: return null
        val sb = StringBuilder(512)
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        val toRecycle: MutableSet<AccessibilityNodeInfo> = HashSet()
        queue.add(root)
        var nodes = 0
        try {
            while (queue.isNotEmpty() && nodes < 250) {
                val node = queue.removeFirst()
                node.text?.let { sb.append(it).append(' ') }
                node.contentDescription?.let { sb.append(it).append(' ') }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    queue.add(child)
                    if (child !== root) toRecycle.add(child)
                }
                nodes++
            }
            // Drain any remaining queue entries — they also need recycling.
            for (n in queue) {
                if (n !== root) toRecycle.add(n)
            }
        } finally {
            // Recycle exactly once per non-root node.
            for (n in toRecycle) runCatching { n.recycle() }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isRunning = false
        runCatching { periodicJob?.cancel() }
        // P2-B: no LocalBroadcastManager to unregister anymore.
        runCatching { unregisterReceiver(screenStateReceiver) }
        runCatching { scope.cancel() }
        runCatching { aiDetector.close() }
        super.onDestroy()
    }
}
