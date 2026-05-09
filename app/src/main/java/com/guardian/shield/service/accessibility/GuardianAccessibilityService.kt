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
import com.guardian.shield.domain.model.ContentTier
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.service.blocker.BlockingEngine
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.RulesEngine
import com.guardian.shield.service.detection.TimedBlockManager
import com.guardian.shield.util.AppClassifier
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
 * v14 (2.1.4) STABILITY PATCH 4:
 *  • CRITICAL FIX: onDestroy() now cancels `scope` BEFORE asking AiDetector
 *    to tear down. Previously the service-scope was cancelled AFTER
 *    closeAsync fired on Scopes.appIo, which allowed in-flight screenshot
 *    callbacks (already launched on Scopes.appDefault) to enqueue fresh
 *    classify() work while teardown ran — a narrow but crashy race on
 *    OEMs that recycle the accessibility service quickly (MIUI, ColorOS).
 *  • DEFENSIVE: AiDetector.startPrefsCache() is now scope-agnostic (it
 *    uses the singleton Scopes.appDefault internally). The scope we pass
 *    here is ignored but we keep the call for source compatibility.
 *
 * v13 (2.1.3):
 *  • aiInFlight reset race resolved via `screenshotInvoked` local.
 *  • Callback uses Scopes.appDefault so a racing onDestroy can't strand
 *    the in-flight flag.
 *
 * v12 (2.1.2):
 *  • Cached canScreenshotCapability at connect-time; SecurityException self-disable.
 *  • mainExecutor null-check.
 *  • Shared Scopes.appIo for AI teardown.
 *  • safeRootInActiveWindow() helper.
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

        private const val EXPLICIT_DEBOUNCE_MS = GuardianConstants.EXPLICIT_DEBOUNCE_MS
        private const val EXPLICIT_CONFIRM_COUNT = GuardianConstants.EXPLICIT_CONFIRM_COUNT

        @Volatile var isRunning: Boolean = false
            private set
    }

    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var blockingEngine: BlockingEngine
    @Inject lateinit var aiDetector: AiDetector
    @Inject lateinit var prefs: GuardianPreferences
    @Inject lateinit var timedBlockManager: TimedBlockManager

    private val scope: CoroutineScope = Scopes.default()
    private var periodicJob: Job? = null

    @Volatile private var lastForegroundPkg: String? = null
    private var lastTextScanMs = 0L
    private val lastAiScanByPkg = HashMap<String, Long>()
    private val aiInFlight = AtomicBoolean(false)

    @Volatile private var isScreenOn = true
    @Volatile private var protectionMasterEnabled = true

    @Volatile private var screenReceiverRegistered = false

    @Volatile private var canScreenshotCapability: Boolean = false

    private val explicitHits = HashMap<String, ArrayDeque<Long>>()

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
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
            } catch (t: Throwable) {
                Timber.w(t, "screenStateReceiver onReceive failed")
            }
        }
    }

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
        } catch (t: Throwable) {
            Timber.e(t, "super.onServiceConnected threw — continuing anyway")
        }
        isRunning = true
        Timber.i("GuardianAccessibilityService connected")

        canScreenshotCapability = runCatching {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                (serviceInfo?.canTakeScreenshot == true)
        }.getOrDefault(false)
        Timber.i("canTakeScreenshot capability=$canScreenshotCapability")

        runCatching { aiDetector.startPrefsCache(scope) }

        scope.launch {
            runCatching { rulesEngine.reload() }
                .onFailure { Timber.w(it, "RulesEngine.reload() at boot failed") }
            runCatching { timedBlockManager.refresh() }
                .onFailure { Timber.w(it, "TimedBlockManager.refresh() at boot failed") }
            runCatching {
                if (aiDetector.isModelAvailable()) aiDetector.ensureLoaded()
            }.onFailure { Timber.e(it, "Legacy model preload failed") }
            runCatching {
                if (aiDetector.isNsfwModelAvailable()) aiDetector.ensureGenderPipelineLoaded()
            }.onFailure { Timber.e(it, "Gender pipeline preload failed") }
        }

        scope.launch {
            runCatching {
                rulesEngine.rulesChanged.collect {
                    Timber.d("RulesEngine reloaded via SharedFlow")
                }
            }.onFailure { Timber.w(it, "rulesChanged collector failed") }
        }

        scope.launch {
            runCatching {
                prefs.protectionEnabled.collect { protectionMasterEnabled = it }
            }.onFailure { Timber.w(it, "protectionEnabled collector failed") }
        }

        registerScreenReceiverSafely()
        startPeriodicAiScanner()
    }

    private fun registerScreenReceiverSafely() {
        if (screenReceiverRegistered) return
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenStateReceiver, filter)
            }
            screenReceiverRegistered = true
        }.onFailure { Timber.w(it, "Failed to register screen state receiver") }
    }

    private fun startPeriodicAiScanner() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!canScreenshotCapability) return
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                val interval = if (isScreenOn) AI_PERIODIC_MS else SCREEN_OFF_PERIODIC_MS
                delay(interval)
                if (!isScreenOn) continue

                try {
                    val pkg = lastForegroundPkg ?: continue
                    if (!rulesEngine.canBlock(pkg)) continue
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
        try {
            event ?: return
            if (!protectionMasterEnabled) return
            val pkg = event.packageName?.toString() ?: return
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowChange(pkg)
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleContentChange(pkg, event)
            }
        } catch (t: Throwable) {
            Timber.e(t, "onAccessibilityEvent crashed — suppressed to keep service alive")
        }
    }

    private fun handleWindowChange(pkg: String) {
        if (pkg == lastForegroundPkg) return
        lastForegroundPkg = pkg

        synchronized(explicitHits) { explicitHits.remove(pkg) }

        when (val result = rulesEngine.evaluatePackage(pkg)) {
            is DetectionResult.Block -> {
                blockingEngine.block(pkg, result.reason, result.detail)
            }

            DetectionResult.Allow -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    canScreenshotCapability &&
                    rulesEngine.canBlock(pkg)
                ) {
                    triggerAiCheck(pkg, force = true)
                    scheduleFollowUpScan(pkg)
                } else if (rulesEngine.canBlock(pkg)) {
                    scheduleLegacyFollowUpChain(pkg)
                }
            }
        }
    }

    private fun scheduleFollowUpScan(pkg: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!canScreenshotCapability) return
        scope.launch {
            delay(AI_FOLLOW_UP_MS)
            if (lastForegroundPkg == pkg) {
                triggerAiCheck(pkg)
            }
        }
    }

    private fun scheduleLegacyFollowUpChain(pkg: String) {
        scope.launch {
            val delays = longArrayOf(500L, 1500L, 3000L)
            for (d in delays) {
                delay(d)
                if (lastForegroundPkg != pkg) return@launch
                if (!rulesEngine.canBlock(pkg)) return@launch
                try {
                    val text = collectVisibleText(safeRootInActiveWindow())
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            canScreenshotCapability &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) {
            triggerAiCheck(pkg)
        }

        val now = System.currentTimeMillis()
        if (now - lastTextScanMs < TEXT_THROTTLE_MS) return
        lastTextScanMs = now

        scope.launch {
            try {
                val text = collectVisibleText(safeRootInActiveWindow())
                if (!text.isNullOrBlank()) {
                    when (val result = rulesEngine.evaluateText(text)) {
                        is DetectionResult.Block -> withContext(Dispatchers.Main) {
                            blockingEngine.block(pkg, BlockReason.KEYWORD_MATCH, result.detail)
                        }
                        DetectionResult.Allow -> Unit
                    }
                }
            } catch (t: Throwable) {
                Timber.w(t, "handleContentChange text scan failed")
            }
        }
    }

    private fun safeRootInActiveWindow(): AccessibilityNodeInfo? =
        runCatching { rootInActiveWindow }.getOrNull()

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

    private fun recordExplicitHit(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(explicitHits) {
            if (explicitHits.size >= MAX_AI_SCAN_MAP && !explicitHits.containsKey(pkg)) {
                val oldestKey = explicitHits.entries
                    .minByOrNull { it.value.lastOrNull() ?: 0L }?.key
                if (oldestKey != null) explicitHits.remove(oldestKey)
            }

            val deque = explicitHits.getOrPut(pkg) { ArrayDeque() }
            while (deque.isNotEmpty() && now - deque.first() > EXPLICIT_DEBOUNCE_MS) {
                deque.removeFirst()
            }
            deque.addLast(now)
            return deque.size >= EXPLICIT_CONFIRM_COUNT
        }
    }

    /**
     * v13 (2.1.3) FIX: aiInFlight reset race resolved.
     *
     * The critical invariant is: aiInFlight must be reset exactly once per
     * compareAndSet(false, true). The previous v12 code released the flag
     * in `finally` (synchronously after takeScreenshot returned) AND inside
     * the async screenshot callback. Between those two resets a new
     * triggerAiCheck could grab the flag, then the stale callback would
     * clear it.
     *
     * New rule:
     *   • If takeScreenshot returns without throwing → callback owns the
     *     reset. `finally` does NOT touch the flag.
     *   • If takeScreenshot throws synchronously → callback will never
     *     fire, so `finally` releases the flag.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun triggerAiCheck(pkg: String, force: Boolean = false) {
        if (!canScreenshotCapability) return
        val now = System.currentTimeMillis()
        val last = lastScanTimeFor(pkg)
        if (!force && now - last < AI_THROTTLE_MS) return
        recordScanTime(pkg, now)

        if (!aiInFlight.compareAndSet(false, true)) return

        scope.launch {
            // v13: track whether the screenshot was actually invoked.
            var screenshotInvoked = false

            try {
                val aiEnabled = aiDetector.cachedAiEnabled
                if (!aiEnabled) {
                    return@launch  // finally below will release.
                }

                val userGender = aiDetector.cachedUserGender
                val genderFeatureOn = userGender != GENDER_NONE && aiDetector.isNsfwModelAvailable()
                val legacyOn        = aiDetector.isModelAvailable()

                if (!genderFeatureOn && !legacyOn) return@launch
                if (genderFeatureOn) aiDetector.ensureGenderPipelineLoaded()
                if (legacyOn)        aiDetector.ensureLoaded()

                if (!rulesEngine.canBlock(pkg)) return@launch

                val executor = runCatching { mainExecutor }.getOrNull()
                if (executor == null) {
                    Timber.w("mainExecutor unavailable — skipping screenshot scan")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    try {
                        takeScreenshot(
                            Display.DEFAULT_DISPLAY,
                            executor,
                            object : TakeScreenshotCallback {
                                override fun onSuccess(screenshot: ScreenshotResult) {
                                    // v13: callback runs in app's Default scope so a
                                    // racing onDestroy can't strand the flag.
                                    // Belt-and-braces: if launch itself ever throws,
                                    // we still release the buffer + flag inline.
                                    val launched = runCatching {
                                    Scopes.appDefault.launch {
                                        var bmp: Bitmap? = null
                                        try {
                                            bmp = Bitmap.wrapHardwareBuffer(
                                                screenshot.hardwareBuffer,
                                                screenshot.colorSpace
                                            )?.copy(Bitmap.Config.ARGB_8888, false)
                                            runCatching { screenshot.hardwareBuffer.close() }

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
                                                val classifyResult = runCatching {
                                                    aiDetector.classify(safeBmp, pkg)
                                                }.onFailure {
                                                    Timber.e(it, "classify() threw")
                                                }.getOrDefault(
                                                    com.guardian.shield.domain.model.ClassificationResult.SAFE
                                                )

                                                when (classifyResult.tier) {
                                                    ContentTier.SUGGESTIVE -> {
                                                        Timber.d(
                                                            "SUGGESTIVE in %s (porn=%.2f hentai=%.2f sexy=%.2f) — log only".format(
                                                                pkg,
                                                                classifyResult.pornScore,
                                                                classifyResult.hentaiScore,
                                                                classifyResult.sexyScore
                                                            )
                                                        )
                                                    }
                                                    ContentTier.EXPLICIT -> {
                                                        val confirmed = recordExplicitHit(pkg)
                                                        if (confirmed) {
                                                            Timber.i(
                                                                "EXPLICIT confirmed in %s — blocking".format(pkg)
                                                            )
                                                            handleConfirmedExplicit(pkg)
                                                        } else {
                                                            Timber.d(
                                                                "EXPLICIT pending (1/$EXPLICIT_CONFIRM_COUNT) in $pkg"
                                                            )
                                                        }
                                                    }
                                                    ContentTier.NATURAL,
                                                    ContentTier.SAFE -> Unit
                                                }
                                            }
                                        } catch (oom: OutOfMemoryError) {
                                            Timber.e(oom, "OOM in screenshot pipeline")
                                        } catch (e: Exception) {
                                            Timber.e(e, "AI screenshot processing error")
                                        } finally {
                                            runCatching { if (bmp?.isRecycled == false) bmp.recycle() }
                                            // v13: SOLE owner of the flag reset
                                            // when screenshotInvoked==true.
                                            aiInFlight.set(false)
                                        }
                                    }
                                    }
                                    if (launched.isFailure) {
                                        Timber.w(launched.exceptionOrNull(), "appDefault.launch failed — releasing flag inline")
                                        runCatching { screenshot.hardwareBuffer.close() }
                                        aiInFlight.set(false)
                                    }
                                }

                                override fun onFailure(errorCode: Int) {
                                    Timber.w("takeScreenshot failed: errorCode=$errorCode")
                                    aiInFlight.set(false)
                                }
                            }
                        )
                        // If we reach here without throwing, the callback is
                        // guaranteed to fire (success or failure) and will
                        // own the flag-reset. Mark and exit.
                        screenshotInvoked = true
                    } catch (sec: SecurityException) {
                        Timber.w(sec, "takeScreenshot SecurityException — disabling for this session")
                        canScreenshotCapability = false
                    } catch (t: Throwable) {
                        Timber.w(t, "takeScreenshot threw synchronously")
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "triggerAiCheck error")
            } finally {
                // v13: only release if the callback path did NOT take over.
                if (!screenshotInvoked) {
                    aiInFlight.set(false)
                }
            }
        }
    }

    private suspend fun handleConfirmedExplicit(pkg: String) {
        val isSourceApp = AppClassifier.isContentSourceApp(pkg)
        if (isSourceApp && rulesEngine.canBlock(pkg)) {
            runCatching {
                timedBlockManager.addTimedBlock(pkg)
            }.onFailure { Timber.w(it, "Failed to add timed block for $pkg") }
        }

        withContext(Dispatchers.Main) {
            blockingEngine.block(
                pkg,
                BlockReason.AI_DETECTION,
                if (isSourceApp)
                    "AI confirmed explicit content — app locked for 15 min"
                else
                    "AI detected explicit content"
            )
        }

        synchronized(explicitHits) { explicitHits.remove(pkg) }
    }

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
                runCatching { node.text?.let { sb.append(it).append(' ') } }
                runCatching { node.contentDescription?.let { sb.append(it).append(' ') } }
                val childCount = runCatching { node.childCount }.getOrDefault(0)
                for (i in 0 until childCount) {
                    val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                    queue.add(child)
                    if (child !== root) toRecycle.add(child)
                }
                nodes++
            }
            for (n in queue) {
                if (n !== root) toRecycle.add(n)
            }
        } catch (t: Throwable) {
            Timber.w(t, "collectVisibleText traversal failed")
        } finally {
            for (n in toRecycle) runCatching { n.recycle() }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isRunning = false
        // v14: cancel our scope FIRST so no in-flight screenshot launch can
        // enqueue fresh classify() work after teardown starts. (Callbacks
        // that are ALREADY on Scopes.appDefault will finish harmlessly
        // since AiDetector singletons outlive the service.)
        runCatching { periodicJob?.cancel() }
        runCatching { scope.cancel() }
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenStateReceiver) }
            screenReceiverRegistered = false
        }
        runCatching { aiDetector.closeAsync(Scopes.appIo) }
        try {
            super.onDestroy()
        } catch (t: Throwable) {
            Timber.w(t, "super.onDestroy threw — suppressed")
        }
    }
}
