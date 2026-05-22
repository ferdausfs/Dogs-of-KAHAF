package com.guardian.shield.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.service.blocker.BlockingEngine
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.ReelScrollDetector
import com.guardian.shield.service.detection.RulesEngine
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

    // ===== TASK 2: Reel/Short scroll addiction detector =====
    @Inject lateinit var reelScrollDetector: ReelScrollDetector

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var isScreenOn = true
    @Volatile private var protectionEnabled = true
    @Volatile private var currentPackage: String? = null
    @Volatile private var lastTextScan = 0L
    @Volatile private var isBlockingInProgress = false

    private val aiScanMap = LinkedHashMap<String, Long>()
    private var periodicJob: Job? = null
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
                    // ✅ Screen off → সব reset
                    isBlockingInProgress = false
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        if (!protectionEnabled || isDeviceLocked()) return
        try {
            val pkg = ev.packageName?.toString().orEmpty()

            // ===== TASK 2: Reel / Short scroll addiction check =====
            // Run on every scroll event, independent of the block pipeline.
            // The host app is NOT blocked — we only surface an Islamic
            // reminder overlay after sustained reel-style scrolling.
            if (ev.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
                && pkg.isNotBlank()
                && reelScrollDetector.REEL_PACKAGES.contains(pkg)
            ) {
                if (reelScrollDetector.recordScroll(pkg)) {
                    reelScrollDetector.markReminderShown(pkg)
                    val ctx: Context = this
                    mainHandler.post {
                        runCatching {
                            ctx.startActivity(
                                ReelReminderActivity.createIntent(ctx, pkg)
                            )
                        }
                    }
                }
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

    private fun isDeviceLocked() = try {
        keyguardManager?.isKeyguardLocked == true
    } catch (_: Throwable) { false }

    private fun isSafePackage(pkg: String) = AppClassifier.isAlwaysAllowedPackage(
        packageName, pkg, rulesEngine.current().inputMethods, homePkg
    )

    // ✅ Fix: currentPackage null + timer reset বন্ধ
    private fun goHomeAndBlock(pkg: String, reason: BlockReason, detail: String) {
        if (isBlockingInProgress) {
            Timber.d("Block in progress, skip: $pkg")
            return
        }
        isBlockingInProgress = true
        // ✅ তুরন্ত clear — periodic scanner stale pkg দেখবে না
        currentPackage = null
        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.postDelayed({
            blockingEngine.block(pkg, reason, detail)
        }, 120)
        // ✅ Timer reset নেই — home screen detect হলে reset হবে
    }

    private fun handleWindowChange(pkg: String) {
        if (pkg.isBlank()) return

        // ===== TASK 2: clear reel session when leaving a reel-host app =====
        // We compare against the package we were previously on; if it was
        // a reel host and the new window is something else, reset its state.
        val previous = currentPackage
        if (previous != null && previous != pkg
            && reelScrollDetector.REEL_PACKAGES.contains(previous)
        ) {
            reelScrollDetector.resetSession(previous)
        }

        // ✅ Safe/home package → সব reset
        if (isSafePackage(pkg)) {
            currentPackage = null
            isBlockingInProgress = false
            // Also drop any reel session for the package we just left to
            // (safe packages are launchers/system UI etc.)
            reelScrollDetector.resetSession(pkg)
            return
        }

        // ✅ Whitelist → track করো কিন্তু block করো না
        if (!rulesEngine.canBlock(pkg)) {
            currentPackage = pkg
            isBlockingInProgress = false
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

        // ✅ Block যোগ্য কিন্তু block হয়নি → flag reset করো
        // এতে পরের AI scan এ block করতে পারবে
        isBlockingInProgress = false

        if (aiDetector.cachedAiEnabled && aiDetector.isLegacyAvailable()
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) triggerAiCheckThrottled(pkg)
    }

    private fun handleContentChange(pkg: String) {
        if (pkg.isBlank() || isSafePackage(pkg)) return
        if (!rulesEngine.canBlock(pkg)) return
        if (isBlockingInProgress) return
        val now = System.currentTimeMillis()
        if (now - lastTextScan < GuardianConstants.TEXT_THROTTLE_MS) return
        lastTextScan = now

        serviceScope.launch {
            try {
                val text = withContext(Dispatchers.Default) { collectVisibleText() }
                if (!text.isNullOrBlank()) {
                    val r = rulesEngine.evaluateText(text)
                    if (r is DetectionResult.Block) {
                        withContext(Dispatchers.Main) { goHomeAndBlock(pkg, r.reason, r.detail) }
                    }
                }
            } catch (t: Throwable) { Timber.e(t) }
        }
    }

    private fun collectVisibleText(): String? {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        val builder = StringBuilder()
        val visited = HashSet<AccessibilityNodeInfo>()
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < GuardianConstants.MAX_NODES_BFS) {
            val node = queue.poll() ?: continue
            if (!visited.add(node)) continue
            count++
            node.text?.toString()?.let { if (it.isNotBlank()) builder.append(it).append(' ') }
            node.contentDescription?.toString()
                ?.let { if (it.isNotBlank()) builder.append(it).append(' ') }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return builder.toString().trim().ifEmpty { null }
    }

    private fun triggerAiCheckThrottled(pkg: String) {
        if (isBlockingInProgress) return
        val now = System.currentTimeMillis()
        synchronized(aiScanMap) {
            val last = aiScanMap[pkg] ?: 0L
            if (now - last < GuardianConstants.AI_THROTTLE_MS) return
            aiScanMap[pkg] = now
            if (aiScanMap.size > GuardianConstants.MAX_AI_SCAN_MAP) {
                val it = aiScanMap.entries.iterator()
                if (it.hasNext()) { it.next(); it.remove() }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) triggerAiCheck(pkg)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun triggerAiCheck(pkg: String) {
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        var bmp: Bitmap? = null
                        serviceScope.launch {
                            try {
                                if (isBlockingInProgress) return@launch
                                val hw = screenshot.hardwareBuffer
                                val cs = screenshot.colorSpace
                                bmp = Bitmap.wrapHardwareBuffer(hw, cs)
                                    ?.copy(Bitmap.Config.ARGB_8888, false)
                                try { hw.close() } catch (_: Throwable) {}
                                val b = bmp ?: return@launch
                                if (!rulesEngine.canBlock(pkg)) return@launch

                                val gender = aiDetector.cachedUserGender
                                var blocked = false

                                if (gender != "NONE"
                                    && aiDetector.isGenderModelAvailable()
                                    && aiDetector.isNsfwGateAvailable()
                                ) {
                                    if (aiDetector.isOppositeGenderNsfw(b, gender)) {
                                        withContext(Dispatchers.Main) {
                                            goHomeAndBlock(pkg, BlockReason.AI_DETECTION, "gender-nsfw")
                                        }
                                        blocked = true
                                    }
                                }
                                if (!blocked && aiDetector.isLegacyAvailable()) {
                                    if (aiDetector.isUnsafe(b)) {
                                        withContext(Dispatchers.Main) {
                                            goHomeAndBlock(pkg, BlockReason.AI_DETECTION, "legacy")
                                        }
                                    }
                                }
                            } catch (t: Throwable) { Timber.e(t, "AI check failed") }
                            finally { try { bmp?.recycle() } catch (_: Throwable) {} }
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        Timber.w("Screenshot fail: $errorCode")
                    }
                })
        } catch (t: Throwable) { Timber.e(t) }
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
                    // ✅ Block চলাকালীন skip
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

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenReceiver) }
        periodicJob?.cancel()
        serviceScope.cancel()
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        try { aiDetector.close() } catch (_: Throwable) {}
    }
}