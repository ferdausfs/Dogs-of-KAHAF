package com.guardian.shield.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.guardian.shield.admin.TamperLogger
import com.guardian.shield.admin.UninstallProtection
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.service.blocker.BlockingEngine
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.FalsePositiveMemory
import com.guardian.shield.service.detection.ReelScrollDetector
import com.guardian.shield.service.detection.RulesEngine
import com.guardian.shield.service.detection.TimeLockManager
import com.guardian.shield.ui.overlay.ReelReminderActivity
import com.guardian.shield.util.AppClassifier
import com.guardian.shield.util.GuardianConstants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.ArrayDeque
import javax.inject.Inject

@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var blockingEngine: BlockingEngine
    @Inject lateinit var aiDetector: AiDetector
    @Inject lateinit var prefs: GuardianPreferences
    @Inject lateinit var reelScrollDetector: ReelScrollDetector
    @Inject lateinit var timeLockManager: TimeLockManager
    @Inject lateinit var falsePositiveMemory: FalsePositiveMemory

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var isScreenOn = true
    @Volatile private var protectionEnabled = true
    @Volatile private var currentPackage: String? = null
    @Volatile private var lastTextScan = 0L
    @Volatile private var lastContentHash: Int = 0
    @Volatile private var isBlockingInProgress = false

    // PRECISION-FIRST (v2.4.2) — only treat a node as scannable *content* when it
    // is large enough to be real content. Feed thumbnails must still be scanned,
    // so this only filters out truly tiny icons / buttons / emoji (<=96px). Profile
    // avatars are handled separately below (excluded from scanning).
    private val MIN_CONTENT_IMAGE_PX = 96

    // STABILITY FIX — auto-reset guard so a stuck flag never freezes detection
    @Volatile private var blockingFlagSetAt = 0L
    private val BLOCKING_FLAG_MAX_HOLD_MS = 6_000L

    // STABILITY FIX — track consecutive screenshot failures so we can back off
    @Volatile private var screenshotFailStreak = 0
    private val SCREENSHOT_FAIL_BACKOFF_THRESHOLD = 5

    private val aiScanMap = LinkedHashMap<String, Long>()
    private var periodicJob: Job? = null
    private var stuckFlagJob: Job? = null
    private var homePkg: String? = null
    private var keyguardManager: KeyguardManager? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> isScreenOn = true
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    currentPackage = null
                    // ✅ Screen off → reset everything
                    clearBlockingFlag("screen-off")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("Accessibility connected")
        homePkg = AppClassifier.getHomePkg(this)
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
            else registerReceiver(screenReceiver, filter)
        }
        isScreenOn =
            (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true

        ioScope.launch {
            try {
                prefs.rulesVersion.collect {
                    try { rulesEngine.reload(); aiDetector.ensureLoaded() }
                    catch (t: Throwable) { Timber.e(t) }
                }
            } catch (t: Throwable) { Timber.e(t) }
        }

        serviceScope.launch {
            try { prefs.protectionEnabled.collect { protectionEnabled = it } }
            catch (t: Throwable) { Timber.e(t) }
        }

        aiDetector.startPrefsCache(serviceScope)
        startPeriodicScanner()
        startStuckFlagWatchdog()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        if (!protectionEnabled || isDeviceLocked()) return
        try {
            val pkg = ev.packageName?.toString().orEmpty()

            // TASK 2 — Reel/Short scroll addiction detection
            if (ev.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && pkg.isNotBlank()) {
                checkReelScroll(pkg)
            }

            when (ev.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowChange(pkg)
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleContentChange(pkg)
                else -> Unit
            }
        } catch (t: Throwable) { Timber.e(t) }
    }

    /**
     * STABILITY FIX — central place to clear the in-progress flag so we never
     * leak it. Also stamps the clear time for the watchdog.
     */
    private fun setBlockingFlag(): Boolean {
        synchronized(this) {
            if (isBlockingInProgress) return false
            isBlockingInProgress = true
            blockingFlagSetAt = System.currentTimeMillis()
            return true
        }
    }

    private fun clearBlockingFlag(reason: String) {
        synchronized(this) {
            if (isBlockingInProgress) {
                Timber.d("Clearing blocking flag: $reason")
            }
            isBlockingInProgress = false
            blockingFlagSetAt = 0L
        }
    }

    /**
     * STABILITY FIX — surfaces and clears the reel reminder regardless of
     * whether the AI sub-system is busy.
     */
    private fun checkReelScroll(pkg: String) {
        try {
            if (isSafePackage(pkg)) return

            // Simple heuristic for Reels/Shorts vs General Feed
            val isShortForm = isShortFormView(pkg)

            val shouldRemind = reelScrollDetector.recordScroll(pkg, isShortForm)
            if (shouldRemind) {
                reelScrollDetector.markReminderShown(pkg)
                mainHandler.post {
                    try {
                        startActivity(ReelReminderActivity.createIntent(this, pkg))
                    } catch (t: Throwable) { Timber.e(t, "Failed to show reel reminder") }
                }
            }
        } catch (t: Throwable) { Timber.e(t, "checkReelScroll failed") }
    }

    private fun isShortFormView(pkg: String): Boolean {
        if (pkg == "com.zhiliaoapp.musically" || pkg == "com.ss.android.ugc.trill") return true

        // Check all windows for "Reels" or "Shorts" text to handle PIP/Split-screen
        // Optimization: For Facebook/Instagram/YouTube, explicitly look for Short-form indicators
        val indicators = when {
            pkg.contains("facebook") || pkg.contains("instagram") -> listOf("Reels")
            pkg.contains("youtube") -> listOf("Shorts")
            else -> listOf("Reels", "Shorts")
        }

        val nodes = findNodesByTextAcrossWindows(indicators)
        // Verify node is actually a visible tab or header, not just random text in a post
        val found = nodes.any { node ->
            val parent = node.parent
            val parentClickable = parent?.isClickable == true
            parent?.recycle()
            node.isVisibleToUser && (node.isClickable || node.isFocused || parentClickable)
        }
        nodes.forEach { it.recycle() }
        return found
    }

    private fun findNodesByTextAcrossWindows(texts: List<String>): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val currentWindows = windows ?: return emptyList()
        for (window in currentWindows) {
            val root = window.root ?: continue
            for (text in texts) {
                val nodes = root.findAccessibilityNodeInfosByText(text)
                if (!nodes.isNullOrEmpty()) {
                    result.addAll(nodes)
                }
            }
            root.recycle()
        }
        return result
    }

    private fun isDeviceLocked() = try {
        keyguardManager?.isKeyguardLocked == true
    } catch (_: Throwable) { false }

    private fun isSafePackage(pkg: String) = AppClassifier.isAlwaysAllowedPackage(
        packageName, pkg, rulesEngine.current().inputMethods, homePkg
    )

    private fun goHomeAndBlock(pkg: String, reason: BlockReason, detail: String) {
        // POST-BLOCK GRACE — right after an AI temp block expires we let the user
        // back in instead of instantly re-blocking on the next scan. This is what
        // makes a "15 min block → then unlocked" actually hold in practice.
        if (reason == BlockReason.AI_DETECTION && blockingEngine.isGracePeriodActive(pkg)) {
            Timber.d("AI grace period active for $pkg — not re-blocking")
            clearBlockingFlag("ai-grace")
            return
        }
        if (!setBlockingFlag()) {
            Timber.d("Block in progress, skip: $pkg")
            return
        }
        // ✅ Immediately clear current package — periodic scanner shouldn't see a stale pkg
        currentPackage = null
        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.postDelayed({
            try { blockingEngine.block(pkg, reason, detail) }
            catch (t: Throwable) { Timber.e(t, "blockingEngine.block failed") }
        }, 120)
    }

    private fun handleWindowChange(pkg: String) {
        if (pkg.isBlank()) return

        // PHASE 5 — Uninstall / Force-stop / Disable protection.
        // If the user opened a package-manager page that targets us, kick them out.
        if (UninstallProtection.isPackageManager(pkg)) {
            try {
                if (UninstallProtection.isManagingOurApp(this)) {
                    Timber.w("Uninstall/Tamper attempt blocked from $pkg")
                    TamperLogger.log(this, "uninstall-attempt")

                    if (timeLockManager.isLocked() || timeLockManager.isInCooldown()) {
                        // Committed Lock active -> Strict block
                        goHomeAndBlock(pkg, BlockReason.TAMPER_ATTEMPT, "committed_lock_active")
                    } else {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                    return
                }
            } catch (t: Throwable) { Timber.e(t, "Uninstall protection check failed") }
        }

        // STABILITY FIX — keyboard / system-UI / home are transient. We must
        // NOT wipe currentPackage when an IME pops over a target app, or the
        // periodic scanner stops scanning.
        if (isSafePackage(pkg)) {
            // Only fully reset state when the user reaches the launcher; for
            // other safe packages (keyboards, system dialogs) we keep the
            // previous currentPackage so AI scanning continues.
            if (pkg == homePkg) {
                currentPackage = null
                clearBlockingFlag("home")
            }
            return
        }

        // ✅ Whitelisted → track but don't block
        if (!rulesEngine.canBlock(pkg)) {
            currentPackage = pkg
            // Optimization: if blocking is in progress (e.g. overlay is launching),
            // don't clear it yet to prevent AI from re-triggering during transition.
            if (!isBlockingInProgress) {
                clearBlockingFlag("whitelist")
            }
            return
        }

        currentPackage = pkg

        // Temp block check
        val tempBlock = blockingEngine.isTempBlocked(pkg)
        if (tempBlock != null) {
            goHomeAndBlock(pkg, BlockReason.APP_BLOCKED, "temp_block:${tempBlock.remainingMinutes}min")
            return
        }

        // Rules check
        val result = rulesEngine.evaluatePackage(pkg)
        if (result is DetectionResult.Block) {
            goHomeAndBlock(pkg, result.reason, result.detail)
            return
        }

        // ✅ Block-eligible but not blocked → reset flag so subsequent AI scan can act
        clearBlockingFlag("window-change-evaluated")

        if (aiDetector.cachedAiEnabled && aiDetector.isLegacyAvailable()
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) triggerAiCheckThrottled(pkg)
    }

    private fun handleContentChange(pkg: String) {
        if (pkg.isBlank() || isSafePackage(pkg)) return
        if (!rulesEngine.canBlock(pkg)) return
        if (isBlockingInProgress) return

        currentPackage = pkg

        val now = System.currentTimeMillis()
        if (now - lastTextScan < GuardianConstants.TEXT_THROTTLE_MS) return
        lastTextScan = now

        serviceScope.launch {
            try {
                val text = withContext(Dispatchers.Default) { collectVisibleText() }
                val contentHash = text?.hashCode() ?: 0
                if (contentHash == lastContentHash && !isBlockingInProgress) return@launch
                lastContentHash = contentHash

                if (!text.isNullOrBlank()) {
                    val r = rulesEngine.evaluateText(text)
                    if (r is DetectionResult.Block) {
                        withContext(Dispatchers.Main) { goHomeAndBlock(pkg, r.reason, r.detail) }
                        return@launch
                    }
                }

                // If text scan didn't block, maybe trigger a faster AI check if it's a major change
                if (aiDetector.cachedAiEnabled && aiDetector.isLegacyAvailable()
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ) {
                    withContext(Dispatchers.Main) { triggerAiCheckThrottled(pkg) }
                }
            } catch (t: Throwable) { Timber.e(t) }
        }
    }

    private fun collectVisibleText(): String? {
        val builder = StringBuilder()
        val currentWindows = windows ?: return null

        for (window in currentWindows) {
            val root = window.root ?: continue
            val visited = HashSet<AccessibilityNodeInfo>()
            val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
            queue.add(root)
            var count = 0
            while (queue.isNotEmpty() && count < GuardianConstants.MAX_NODES_BFS) {
                val node = queue.poll() ?: continue
                if (!visited.add(node)) {
                    node.recycle()
                    continue
                }
                count++
                node.text?.toString()?.let { if (it.isNotBlank()) builder.append(it).append(' ') }
                node.contentDescription?.toString()
                    ?.let { if (it.isNotBlank()) builder.append(it).append(' ') }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) queue.add(child)
                }
            }
            // Cleanup: recycle both visited and queued nodes to prevent leaks
            visited.forEach { it.recycle() }
            while (queue.isNotEmpty()) {
                queue.poll()?.recycle()
            }
        }
        return builder.toString().trim().ifEmpty { null }
    }

    private fun triggerAiCheckThrottled(pkg: String) {
        if (isBlockingInProgress) return

        // STABILITY FIX — back off briefly after a screenshot failure storm
        if (screenshotFailStreak >= SCREENSHOT_FAIL_BACKOFF_THRESHOLD) {
            Timber.w("Screenshot back-off active ($screenshotFailStreak fails)")
            return
        }

        val now = System.currentTimeMillis()

        // Use a more aggressive throttle if the user is scrolling to catch fleeting images
        val throttleMs = if (isScrollingActive()) {
            GuardianConstants.AI_THROTTLE_MS / 2
        } else {
            GuardianConstants.AI_THROTTLE_MS
        }

        synchronized(aiScanMap) {
            val last = aiScanMap[pkg] ?: 0L
            if (now - last < throttleMs) return
            aiScanMap[pkg] = now
            if (aiScanMap.size > GuardianConstants.MAX_AI_SCAN_MAP) {
                val it = aiScanMap.entries.iterator()
                if (it.hasNext()) { it.next(); it.remove() }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) triggerAiCheck(pkg)
    }

    private fun isScrollingActive(): Boolean {
        // ReelScrollDetector tracks scroll events
        return reelScrollDetector.isCurrentlyScrolling()
    }

    private fun collectImageRegions(): List<android.graphics.Rect> {
        val regions = mutableListOf<android.graphics.Rect>()
        val currentWindows = try { windows } catch (t: Throwable) { null } ?: return emptyList()

        val displayWidth = resources.displayMetrics.widthPixels
        val displayHeight = resources.displayMetrics.heightPixels

        for (window in currentWindows) {
            val root = try { window.root } catch (t: Throwable) { null } ?: continue
            val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
            queue.add(root)
            var count = 0
            while (queue.isNotEmpty() && count < 150) {
                val node = queue.poll() ?: continue
                count++

                val className = node.className?.toString() ?: ""
                val viewId = node.viewIdResourceName?.toString() ?: ""
                val isImage = className.contains("ImageView") || className.contains("Image") ||
                        viewId.contains("image", ignoreCase = true) ||
                        viewId.contains("photo", ignoreCase = true) ||
                        viewId.contains("video", ignoreCase = true) ||
                        viewId.contains("story", ignoreCase = true)

                // PROFILE FIX — profile pictures / avatars are user *identity*, not
                // content, and their tiny crops are a huge source of false AI blocks
                // (they contain a person's face/hair and get cropped without any
                // context). Exclude them explicitly so feed thumbnails still scan.
                val isProfileImage =
                    viewId.contains("avatar", ignoreCase = true) ||
                    viewId.contains("profile", ignoreCase = true) ||
                    viewId.contains("profile_pic", ignoreCase = true) ||
                    viewId.contains("profilepic", ignoreCase = true) ||
                    viewId.contains("user_image", ignoreCase = true) ||
                    viewId.contains("dp_", ignoreCase = true)

                if (isImage && !isProfileImage) {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    // Ignore truly tiny icons/buttons/emoji; scan everything else
                    // (including feed thumbnails). Small crops are still protected
                    // by AiDetector so they can't block on a borderline score.
                    if (rect.width() >= MIN_CONTENT_IMAGE_PX && rect.height() >= MIN_CONTENT_IMAGE_PX) {
                        // Ensure rect is within screen bounds to avoid bad crops
                        rect.left = rect.left.coerceIn(0, displayWidth)
                        rect.top = rect.top.coerceIn(0, displayHeight)
                        rect.right = rect.right.coerceIn(0, displayWidth)
                        rect.bottom = rect.bottom.coerceIn(0, displayHeight)
                        if (rect.width() > 64 && rect.height() > 64) {
                            regions.add(rect)
                        }
                    }
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
                node.recycle()
            }
            while (queue.isNotEmpty()) { queue.poll()?.recycle() }
        }

        // Prioritize: Center-Bottom > Center > Size
        return regions.distinct()
            .sortedByDescending { rect ->
                val area = rect.width() * rect.height()
                val centerY = rect.centerY()
                val centerX = rect.centerX()

                var weight = 1.0f
                // Center-weighted
                if (centerX > displayWidth / 4 && centerX < 3 * displayWidth / 4) weight += 0.2f
                // Bottom-weighted (scrolling content)
                if (centerY > displayHeight / 3) weight += 0.3f

                area * weight
            }
            .take(10)
    }

    private suspend fun runContentAwareScan(
        fullBitmap: Bitmap,
        regions: List<android.graphics.Rect>,
        pkg: String
    ): Boolean {
        // High density scanning for detected image regions
        for (rect in regions) {
            if (isBlockingInProgress) return true

            // Validate rect within bitmap bounds
            val left = rect.left.coerceAtLeast(0)
            val top = rect.top.coerceAtLeast(0)
            val width = rect.width().coerceAtMost(fullBitmap.width - left)
            val height = rect.height().coerceAtMost(fullBitmap.height - top)

            // Skip only truly tiny regions (icons/buttons). Thumbnails and avatars
            // that slipped through are still scanned here, but the small-crop
            // protection in AiDetector prevents a borderline score from blocking.
            if (width < MIN_CONTENT_IMAGE_PX || height < MIN_CONTENT_IMAGE_PX) continue

            var regionBmp: Bitmap? = null
            try {
                regionBmp = Bitmap.createBitmap(fullBitmap, left, top, width, height)
                if (aiDetector.isLegacyAvailable() && aiDetector.isUnsafe(regionBmp)) {
                    if (currentPackage == pkg) {
                        // LEARNING MEMORY — keep the offending region so the overlay
                        // can offer "this was a false block" and never block it again.
                        falsePositiveMemory.rememberCandidate(
                            falsePositiveMemory.computeSignature(regionBmp)
                        )
                        withContext(Dispatchers.Main) {
                            goHomeAndBlock(pkg, BlockReason.AI_DETECTION, "content-aware-legacy")
                        }
                        return true
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "Region scan failed")
            } finally {
                regionBmp?.recycle()
            }
        }
        return false
    }

    /**
     * STABILITY FIX — on screenshot failure, remove the throttle entry so the
     * next scan isn't suppressed for AI_THROTTLE_MS.
     */
    private fun clearAiThrottleEntry(pkg: String) {
        synchronized(aiScanMap) { aiScanMap.remove(pkg) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun triggerAiCheck(pkg: String) {
        try {
            // Optimization: If scrolling, prioritize content-aware scanning of ImageViews
            val targetRegions = if (isScrollingActive()) collectImageRegions() else emptyList()

            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        screenshotFailStreak = 0
                        var bmp: Bitmap? = null
                        serviceScope.launch {
                            try {
                                if (isBlockingInProgress) return@launch
                                val hw = screenshot.hardwareBuffer
                                val cs = screenshot.colorSpace
                                bmp = try {
                                    Bitmap.wrapHardwareBuffer(hw, cs)
                                        ?.copy(Bitmap.Config.ARGB_8888, false)
                                } catch (t: Throwable) {
                                    Timber.w(t, "Bitmap.wrapHardwareBuffer failed")
                                    null
                                }
                                try { hw.close() } catch (_: Throwable) {}
                                val b = bmp ?: return@launch
                                if (!rulesEngine.canBlock(pkg)) return@launch

                                // ✅ Ensure the app we are scanning is STILL the one in the foreground
                                if (currentPackage != pkg) {
                                    Timber.d("Package changed during AI scan (from $pkg to $currentPackage). Skipping block.")
                                    return@launch
                                }

                                // Content-Aware Pre-scan: Check detected image regions first
                                var blocked = false
                                if (targetRegions.isNotEmpty()) {
                                    blocked = runContentAwareScan(b, targetRegions, pkg)
                                }

                                if (!blocked) {
                                    if (aiDetector.isLegacyAvailable()) {
                                        if (aiDetector.isUnsafe(b)) {
                                            // ✅ Final sanity check before blocking
                                            if (currentPackage == pkg) {
                                                // LEARNING MEMORY — keep the frame so the
                                                // overlay can offer "this was a false block".
                                                falsePositiveMemory.rememberCandidate(
                                                    falsePositiveMemory.computeSignature(b)
                                                )
                                                withContext(Dispatchers.Main) {
                                                    goHomeAndBlock(pkg, BlockReason.AI_DETECTION, "legacy")
                                                }
                                                blocked = true
                                            }
                                        }
                                    }
                                }

                                if (!blocked) {
                                    clearBlockingFlag("ai-check-safe")
                                }
                            } catch (t: Throwable) { Timber.e(t, "AI check failed") }
                            finally { try { bmp?.recycle() } catch (_: Throwable) {} }
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        screenshotFailStreak++
                        Timber.w("Screenshot fail: $errorCode (streak=$screenshotFailStreak)")
                        // STABILITY FIX — let the next scan retry immediately
                        clearAiThrottleEntry(pkg)
                    }
                })
        } catch (t: Throwable) {
            screenshotFailStreak++
            Timber.e(t, "takeScreenshot threw (streak=$screenshotFailStreak)")
            clearAiThrottleEntry(pkg)
        }
    }

    private fun startPeriodicScanner() {
        periodicJob?.cancel()
        periodicJob = serviceScope.launch {
            while (isActive) {
                try {
                    delay(
                        if (!isScreenOn) GuardianConstants.SCREEN_OFF_PERIODIC_MS
                        else GuardianConstants.AI_PERIODIC_MS
                    )

                    if (!isScreenOn || !protectionEnabled || isDeviceLocked()) continue
                    if (isBlockingInProgress) continue

                    val pkg = currentPackage ?: continue
                    if (isSafePackage(pkg)) continue
                    if (!rulesEngine.canBlock(pkg)) continue

                    val tempBlock = blockingEngine.isTempBlocked(pkg)
                    if (tempBlock != null) {
                        withContext(Dispatchers.Main) {
                            goHomeAndBlock(
                                pkg, BlockReason.APP_BLOCKED,
                                "temp_block:${tempBlock.remainingMinutes}min"
                            )
                        }
                        continue
                    }

                    val r = rulesEngine.evaluatePackage(pkg)
                    if (r is DetectionResult.Block) {
                        withContext(Dispatchers.Main) { goHomeAndBlock(pkg, r.reason, r.detail) }
                        continue
                    }

                    if (aiDetector.cachedAiEnabled && aiDetector.isLegacyAvailable()
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ) withContext(Dispatchers.Main) { triggerAiCheckThrottled(pkg) }

                } catch (t: Throwable) { Timber.e(t) }
            }
        }
    }

    /**
     * STABILITY FIX — every few seconds, if [isBlockingInProgress] has been
     * stuck for longer than [BLOCKING_FLAG_MAX_HOLD_MS], force-clear it. This
     * is the single biggest cause of "AI detection becomes silent" complaints.
     */
    private fun startStuckFlagWatchdog() {
        stuckFlagJob?.cancel()
        stuckFlagJob = serviceScope.launch {
            while (isActive) {
                try {
                    delay(3_000L)
                    if (isBlockingInProgress && blockingFlagSetAt > 0) {
                        val held = System.currentTimeMillis() - blockingFlagSetAt
                        if (held > BLOCKING_FLAG_MAX_HOLD_MS) {
                            Timber.w("isBlockingInProgress stuck for ${held}ms — forcing reset")
                            clearBlockingFlag("watchdog-timeout")
                        }
                    }
                    // STABILITY FIX — periodic screenshot streak decay so
                    // back-off doesn't last forever once the device recovers.
                    if (screenshotFailStreak > 0
                        && screenshotFailStreak >= SCREENSHOT_FAIL_BACKOFF_THRESHOLD) {
                        // Try one slow recovery scan
                        screenshotFailStreak = SCREENSHOT_FAIL_BACKOFF_THRESHOLD - 1
                    }
                } catch (t: Throwable) { Timber.e(t, "watchdog tick failed") }
            }
        }
    }

    override fun onInterrupt() {
        Timber.w("Accessibility service interrupted")
        clearBlockingFlag("interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenReceiver) }
        periodicJob?.cancel()
        stuckFlagJob?.cancel()
        serviceScope.cancel()
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        try { aiDetector.close() } catch (_: Throwable) {}
    }
}
