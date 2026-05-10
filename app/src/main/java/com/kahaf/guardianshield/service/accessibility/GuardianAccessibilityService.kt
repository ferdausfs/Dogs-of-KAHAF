package com.kahaf.guardianshield.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.kahaf.guardianshield.R
import com.kahaf.guardianshield.data.classifier.TfLiteNsfwClassifier
import com.kahaf.guardianshield.domain.model.BlockReason
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import com.kahaf.guardianshield.domain.usecase.AnalyzeFrameUseCase
import com.kahaf.guardianshield.domain.usecase.AutoLockSourceAppUseCase
import com.kahaf.guardianshield.domain.usecase.EvaluateForegroundAppUseCase
import com.kahaf.guardianshield.domain.usecase.RecordBlockEventUseCase
import com.kahaf.guardianshield.domain.usecase.ScanTextForKeywordsUseCase
import com.kahaf.guardianshield.domain.usecase.ScanUrlForDomainUseCase
import com.kahaf.guardianshield.service.foreground.GuardianForegroundService
import com.kahaf.guardianshield.service.overlay.BlockOverlayActivity
import com.kahaf.guardianshield.util.AppClassifier
import com.kahaf.guardianshield.util.GuardianConstants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Heart of the protection logic.
 *
 *  - Filters tightly to TYPE_WINDOW_STATE_CHANGED + TYPE_VIEW_TEXT_CHANGED +
 *    TYPE_WINDOW_CONTENT_CHANGED to keep wakelock burn minimal.
 *  - Coalesces text scans with a 250ms debounce.
 *  - Wraps every callback in try/catch — never throws.
 *  - Starts the foreground service on connect so the system keeps us alive.
 *
 * v3.0.0: when the foreground app is a known browser, we additionally feed
 * collected text through [ScanUrlForDomainUseCase] so URL bars / page
 * content matched against the user's blocked-domain list trigger a block.
 *
 * v3.1.0: AI on-screen NSFW scanning wired via [AnalyzeFrameUseCase].
 *  - Periodic scanner uses [AccessibilityService.takeScreenshot] (API 30+).
 *  - Falls back silently on older devices.
 *  - Honours a per-package throttle on TYPE_WINDOW_CONTENT_CHANGED.
 *  - On confirmed EXPLICIT verdict, fires AI_NSFW block + (when applicable)
 *    triggers the 15-min source-based auto-lock via [AutoLockSourceAppUseCase].
 *
 * v3.1.1 FIXES:
 *  - Skip the screenshot + inference cost entirely when the classifier model
 *    isn't loaded (no asset bundled and no custom import). Previously we'd
 *    burn battery scanning every 850ms and then return SAFE.
 *  - The content-change throttle map now also tracks periodic-scan timestamps
 *    so we don't double-scan back-to-back when a content event lands inside
 *    the periodic loop's window.
 */
@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    @Inject lateinit var evaluateForegroundApp: EvaluateForegroundAppUseCase
    @Inject lateinit var scanTextForKeywords: ScanTextForKeywordsUseCase
    @Inject lateinit var scanUrlForDomain: ScanUrlForDomainUseCase
    @Inject lateinit var recordBlockEvent: RecordBlockEventUseCase
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var analyzeFrameUseCase: AnalyzeFrameUseCase
    @Inject lateinit var autoLockSourceApp: AutoLockSourceAppUseCase
    @Inject lateinit var classifier: TfLiteNsfwClassifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var lastForegroundPackage: String? = null
    @Volatile private var lastBlockedAtMs: Long = 0L
    private var pendingScanJob: Job? = null
    private var aiPeriodicJob: Job? = null

    /** Per-package last-AI-scan timestamp (covers BOTH content-change AND periodic). */
    private val lastAiScanByPkg = HashMap<String, Long>()

    private val powerManager: PowerManager? by lazy {
        runCatching { getSystemService(Context.POWER_SERVICE) as? PowerManager }.getOrNull()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            instance = this
            // Start FG so the system gives us higher priority and a visible notif.
            GuardianForegroundService.start(applicationContext)
            // Kick off the AI periodic scan loop (API 30+ only).
            startAiPeriodicScan()
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
                    // Opportunistic AI scan on content changes — strictly
                    // throttled per-package to honour AI_THROTTLE_MS.
                    maybeScheduleAiScanOnContentChange(safePkg)
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

                // 1) Browser package? Try domain blocking first — URL bar text
                //    is typically the most authoritative signal for browsers.
                if (pkg in BROWSER_PACKAGES) {
                    val domainHit = scanUrlForDomain(text)
                    if (domainHit != null) {
                        triggerBlock(
                            pkg = pkg,
                            reason = BlockReason.KEYWORD,
                            detail = "Blocked domain: ${domainHit.domain}",
                            lockedUntilMs = 0L,
                            reasonRes = R.string.blk_reason_domain
                        )
                        return@launch
                    }
                }

                // 2) Generic keyword scan for every package (browsers included).
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

    // ─────────────────────────────────────────────────────────────────────
    //  AI on-screen NSFW scanning  (v3.1.0)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Periodic AI scan loop. Runs every [GuardianConstants.AI_PERIODIC_MS]
     * (or [GuardianConstants.SCREEN_OFF_PERIODIC_MS] when the screen is off).
     *
     * Silent no-op on API < 30 — [takeScreenshot] is unavailable.
     *
     * v3.1.1: also no-op when the classifier reports `isModelLoaded == false`,
     * so we don't pay the screenshot + decode cost when there's nothing
     * meaningful to classify against.
     */
    private fun startAiPeriodicScan() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.i(TAG, "AI periodic scan disabled — requires API 30+")
            return
        }
        aiPeriodicJob?.cancel()
        aiPeriodicJob = scope.launch {
            while (true) {
                try {
                    val sleep = if (powerManager?.isInteractive == false) {
                        GuardianConstants.SCREEN_OFF_PERIODIC_MS
                    } else {
                        GuardianConstants.AI_PERIODIC_MS
                    }
                    delay(sleep)

                    val protectionOn = settingsRepository.appSettings.first().protectionEnabled
                    if (!protectionOn) continue

                    // Skip work entirely while the screen is off — saves battery.
                    if (powerManager?.isInteractive == false) continue

                    // v3.1.1: skip if no model is loaded (asset missing AND no custom
                    // import). Otherwise we'd just screenshot → decode → classify SAFE
                    // every tick.
                    if (!classifier.isModelLoaded.value) continue

                    val pkg = lastForegroundPackage ?: continue
                    if (pkg == applicationContext.packageName) continue

                    // Honour the same per-package throttle as content-change scans
                    // so we don't fire two screenshots back-to-back.
                    val now = System.currentTimeMillis()
                    val lastScan = lastAiScanByPkg[pkg] ?: 0L
                    if (now - lastScan < GuardianConstants.AI_THROTTLE_MS) continue
                    lastAiScanByPkg[pkg] = now

                    val confirmedBlock = runAiScanFor(pkg)
                    if (confirmedBlock) {
                        // After a confirmed block, throttle harder so we don't
                        // re-fire on every following frame of the same surface.
                        delay(GuardianConstants.EXPLICIT_DEBOUNCE_MS)
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.e(TAG, "AI periodic loop iteration error", t)
                }
            }
        }
    }

    /**
     * Lightweight per-package throttle for AI scans driven by content-changed
     * events. The periodic loop is independent and runs regardless.
     */
    private fun maybeScheduleAiScanOnContentChange(pkg: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!classifier.isModelLoaded.value) return  // v3.1.1: skip when no model
        val now = System.currentTimeMillis()
        val last = lastAiScanByPkg[pkg] ?: 0L
        if (now - last < GuardianConstants.AI_THROTTLE_MS) return
        lastAiScanByPkg[pkg] = now
        // Cap the map so a long session doesn't grow it unbounded.
        if (lastAiScanByPkg.size > GuardianConstants.MAX_AI_SCAN_MAP) {
            val cutoff = now - 60_000L
            lastAiScanByPkg.entries.removeAll { it.value < cutoff }
        }
        scope.launch {
            try {
                val protectionOn = settingsRepository.appSettings.first().protectionEnabled
                if (!protectionOn) return@launch
                if (powerManager?.isInteractive == false) return@launch
                runAiScanFor(pkg)
            } catch (t: Throwable) {
                Log.e(TAG, "content-change AI scan error", t)
            }
        }
    }

    /**
     * Performs a single AI scan iteration for [pkg]. Returns true iff the
     * outcome was confirmed and a block was fired.
     *
     * Caller must already have verified protection is enabled.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun runAiScanFor(pkg: String): Boolean {
        // Skip whitelisted system surfaces.
        val ime = runCatching { AppClassifier.loadInputMethodPackages(applicationContext) }
            .getOrDefault(emptySet())
        if (AppClassifier.isAlwaysAllowedPackage(applicationContext.packageName, pkg, ime)) {
            return false
        }

        val ai = settingsRepository.aiSettings.first()
        // AI runs only on apps the user (or defaults) has marked as content
        // sources. Saves battery and avoids surprising scans of native UIs.
        if (pkg !in ai.contentSourcePackages) return false

        var bitmap: Bitmap? = null
        try {
            bitmap = captureScreenshotSuspend() ?: return false
            // Skip frames that are too small to be meaningful.
            if (bitmap.width < ai.minImageSize || bitmap.height < ai.minImageSize) {
                return false
            }

            // Inference on Default dispatcher (already serialised inside
            // TfLiteNsfwClassifier via Mutex).
            val outcome = withContext(Dispatchers.Default) {
                analyzeFrameUseCase.analyze(pkg, bitmap!!)
            }

            if (outcome.confirmed) {
                val confidence = outcome.result.confidence
                triggerBlock(
                    pkg = pkg,
                    reason = BlockReason.AI_NSFW,
                    detail = "AI score: $confidence",
                    lockedUntilMs = 0L,
                    reasonRes = R.string.blk_reason_ai
                )
                // 15-min source-based auto-lock for known content-source apps.
                if (AppClassifier.isContentSourceApp(pkg) &&
                    pkg in ai.contentSourcePackages
                ) {
                    runCatching {
                        autoLockSourceApp(pkg, "AI EXPLICIT detection")
                    }.onFailure { Log.e(TAG, "autoLockSourceApp failed", it) }
                }
                return true
            }
            return false
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "runAiScanFor error", t)
            return false
        } finally {
            try {
                if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
            } catch (_: Throwable) { /* ignore */ }
        }
    }

    /**
     * Wraps [AccessibilityService.takeScreenshot] in a suspend function. The
     * platform callback fires on the provided executor — we copy the
     * HardwareBuffer into a software Bitmap before resuming so downstream
     * inference can read pixels safely.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureScreenshotSuspend(): Bitmap? =
        suspendCancellableCoroutine { cont ->
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    { command -> command.run() },                           // direct executor
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            // Consume on Main as required by the platform contract,
                            // then deliver the bitmap synchronously.
                            var bmp: Bitmap? = null
                            try {
                                val hb: HardwareBuffer = screenshot.hardwareBuffer
                                val cs = screenshot.colorSpace
                                val wrapped = Bitmap.wrapHardwareBuffer(hb, cs)
                                // Convert to ARGB_8888 software bitmap so TFLite
                                // can read pixels (HARDWARE bitmaps are read-only).
                                bmp = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                wrapped?.recycle()
                                hb.close()
                            } catch (t: Throwable) {
                                Log.e(TAG, "screenshot decode failed", t)
                            }
                            if (cont.isActive) cont.resume(bmp)
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "takeScreenshot failed: $errorCode")
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            } catch (t: Throwable) {
                Log.e(TAG, "takeScreenshot threw", t)
                if (cont.isActive) cont.resume(null)
            }
        }

    // ─────────────────────────────────────────────────────────────────────

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
            lockedUntilMs = lockedUntilMs,
            reasonName = reason.name
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
            aiPeriodicJob?.cancel()
            aiPeriodicJob = null
            scope.cancel()
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GuardianA11y"
        private const val MAX_DEPTH = 24
        private const val MAX_TEXT_BYTES = 8192

        /** Known major browser packages we run domain-blocking against. */
        private val BROWSER_PACKAGES: Set<String> = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fennec_fdroid",
            "com.brave.browser",
            "com.sec.android.app.sbrowser",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.duckduckgo.mobile.android",
            "org.mozilla.focus",
            "com.microsoft.emmx",
            "com.vivaldi.browser",
            "com.kiwibrowser.browser"
        )

        @Volatile var instance: GuardianAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null

        fun goHomeNow() = instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
    }
}
