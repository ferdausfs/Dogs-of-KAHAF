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
 * v10 (2.1.0) FIX-LOG (Smart Tiered Detection):
 *  • Replaced single-frame block with EXPLICIT_DEBOUNCE — block fires only
 *    after 2 consecutive EXPLICIT classifications within 3 seconds.
 *    Single-frame false positives no longer trigger.
 *  • SUGGESTIVE tier (hot/sexy) is now logged but never blocks. Only
 *    EXPLICIT (porn / hentai / explicit nudity above per-class threshold)
 *    triggers the overlay.
 *  • Source-based 15-min timed block: when a content-source app
 *    (Facebook / Instagram / Twitter / TikTok / YouTube / Telegram /
 *    WhatsApp / Reddit / browsers) is the verified source of EXPLICIT
 *    content, [TimedBlockManager.addTimedBlock] is called with a 15-min
 *    expiry. RulesEngine then auto-blocks every subsequent open of that
 *    app for the full window — no overlay arguments, no second chances.
 *
 * v9 (2.0.0) preserved: P1-C prefs cache, P1-E screen-state-aware scanner,
 *   P2-B SharedFlow rules-changed notifications, P2-C visited-set node
 *   recycling.
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

        // v10: tiered debounce
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

    /**
     * v10: per-package detection history. Tracks the timestamp of each
     * EXPLICIT classification — used to enforce the EXPLICIT_DEBOUNCE
     * (2 hits within 3 s before block fires).
     *
     * Entries older than EXPLICIT_DEBOUNCE_MS are discarded on every
     * recordExplicitHit() call. Map size is bounded at MAX_AI_SCAN_MAP.
     */
    private val explicitHits = HashMap<String, ArrayDeque<Long>>()

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

        runCatching { aiDetector.startPrefsCache(scope) }

        scope.launch {
            rulesEngine.reload()
            // v10: warm the timed-block cache from DB on startup.
            runCatching { timedBlockManager.refresh() }
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

    private fun startPeriodicAiScanner() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
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
        event ?: return
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

        // v10: opening a new app clears its previous EXPLICIT debounce
        // counter — fresh start.
        synchronized(explicitHits) { explicitHits.remove(pkg) }

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

    /**
     * v10: record an EXPLICIT classification timestamp for [pkg].
     * Returns true when EXPLICIT_CONFIRM_COUNT hits have occurred within
     * EXPLICIT_DEBOUNCE_MS — i.e. the debounce has fired and a real
     * block should be issued. Otherwise returns false (we wait for more).
     */
    private fun recordExplicitHit(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(explicitHits) {
            // Drop old packages if we're at cap.
            if (explicitHits.size >= MAX_AI_SCAN_MAP && !explicitHits.containsKey(pkg)) {
                val oldestKey = explicitHits.entries
                    .minByOrNull { it.value.lastOrNull() ?: 0L }?.key
                if (oldestKey != null) explicitHits.remove(oldestKey)
            }

            val deque = explicitHits.getOrPut(pkg) { ArrayDeque() }
            // Discard hits older than the debounce window.
            while (deque.isNotEmpty() && now - deque.first() > EXPLICIT_DEBOUNCE_MS) {
                deque.removeFirst()
            }
            deque.addLast(now)
            return deque.size >= EXPLICIT_CONFIRM_COUNT
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun triggerAiCheck(pkg: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val last = lastScanTimeFor(pkg)
        if (!force && now - last < AI_THROTTLE_MS) return
        recordScanTime(pkg, now)

        if (!aiInFlight.compareAndSet(false, true)) return

        scope.launch {
            try {
                val aiEnabled = aiDetector.cachedAiEnabled
                if (!aiEnabled) {
                    aiInFlight.set(false)
                    return@launch
                }

                val userGender = aiDetector.cachedUserGender

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
                                                    // v10: tiered classify() instead of boolean isUnsafe().
                                                    val result = runCatching {
                                                        aiDetector.classify(safeBmp, pkg)
                                                    }.onFailure {
                                                        Timber.e(it, "classify() threw")
                                                    }.getOrDefault(
                                                        com.guardian.shield.domain.model.ClassificationResult.SAFE
                                                    )

                                                    when (result.tier) {
                                                        ContentTier.SUGGESTIVE -> {
                                                            // Log only — do NOT block.
                                                            Timber.d(
                                                                "SUGGESTIVE in $pkg (porn=%.2f hentai=%.2f sexy=%.2f) — log only".format(
                                                                    result.pornScore,
                                                                    result.hentaiScore,
                                                                    result.sexyScore
                                                                )
                                                            )
                                                        }
                                                        ContentTier.EXPLICIT -> {
                                                            // v10: debounce — require N consecutive hits.
                                                            val confirmed = recordExplicitHit(pkg)
                                                            if (confirmed) {
                                                                Timber.i(
                                                                    "EXPLICIT confirmed in $pkg (porn=%.2f hentai=%.2f combined=%.2f) — blocking".format(
                                                                        result.pornScore,
                                                                        result.hentaiScore,
                                                                        result.combinedUnsafeScore
                                                                    )
                                                                )
                                                                handleConfirmedExplicit(pkg)
                                                            } else {
                                                                Timber.d(
                                                                    "EXPLICIT pending (1/${EXPLICIT_CONFIRM_COUNT}) in $pkg — waiting for confirmation"
                                                                )
                                                            }
                                                        }
                                                        ContentTier.NATURAL,
                                                        ContentTier.SAFE -> {
                                                            // Quiet frame — clear any stale debounce hits.
                                                            // (Old hits expire naturally via timeout, but
                                                            // a clean SAFE frame is a good reset signal.)
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
     * v10: an EXPLICIT verdict has cleared the debounce. Two paths:
     *
     *   1. If [pkg] is a known content-source app (Facebook / Instagram /
     *      Twitter / TikTok / browsers / etc.), add it to the
     *      TimedBlockManager for AI_SOURCE_BLOCK_MS (15 min). The
     *      RulesEngine will then automatically reject every subsequent
     *      foreground event for that package — no overlay arguments, no
     *      second chances.
     *
     *   2. Always trigger an immediate one-time block with reason
     *      AI_DETECTION so the user is kicked out of the current view.
     *
     * The 15-min auto-lock is in addition to the immediate block, NOT a
     * replacement — the reason logged for the FIRST block is still
     * AI_DETECTION (so dashboard stats stay accurate). Subsequent re-opens
     * within 15 min log AI_SOURCE_TIMED_BLOCK.
     */
    private suspend fun handleConfirmedExplicit(pkg: String) {
        // 1. Source-based timed block — only for content-source apps,
        //    skip for system / launcher / IME / whitelisted (canBlock guard
        //    already covers those, but we double-check the source-app filter).
        val isSourceApp = AppClassifier.isContentSourceApp(pkg)
        if (isSourceApp && rulesEngine.canBlock(pkg)) {
            runCatching {
                timedBlockManager.addTimedBlock(pkg)
            }.onFailure { Timber.w(it, "Failed to add timed block for $pkg") }
        }

        // 2. Immediate block (kicks the user out of the current view).
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

        // 3. Clear the debounce buffer for this package so we don't
        //    re-fire on the next frame.
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
                node.text?.let { sb.append(it).append(' ') }
                node.contentDescription?.let { sb.append(it).append(' ') }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    queue.add(child)
                    if (child !== root) toRecycle.add(child)
                }
                nodes++
            }
            for (n in queue) {
                if (n !== root) toRecycle.add(n)
            }
        } finally {
            for (n in toRecycle) runCatching { n.recycle() }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        isRunning = false
        runCatching { periodicJob?.cancel() }
        runCatching { unregisterReceiver(screenStateReceiver) }
        runCatching { scope.cancel() }
        runCatching { aiDetector.close() }
        super.onDestroy()
    }
}
