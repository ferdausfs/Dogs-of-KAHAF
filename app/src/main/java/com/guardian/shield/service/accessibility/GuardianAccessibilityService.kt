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
import com.guardian.shield.service.detection.RulesEngine
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

    // ✅ Fix: service owned scope — onDestroy এ cancel হবে
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ✅ Main thread handler — performGlobalAction এর জন্য
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var isScreenOn: Boolean = true
    @Volatile private var protectionEnabled: Boolean = true
    @Volatile private var currentPackage: String? = null
    @Volatile private var lastTextScan: Long = 0L
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
                    try {
                        rulesEngine.reload()
                        aiDetector.ensureLoaded()
                        Timber.d("Reloaded (v=$it)")
                    } catch (t: Throwable) { Timber.e(t) }
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

    // ✅ Fix: goHome() main thread এ call — then block
    private fun goHomeAndBlock(pkg: String, reason: BlockReason, detail: String) {
        // onAccessibilityEvent main thread এ চলে — performGlobalAction এখানে safe
        performGlobalAction(GLOBAL_ACTION_HOME)
        // ✅ 120ms delay — home screen appear হওয়ার পরে overlay launch করো
        mainHandler.postDelayed({
            blockingEngine.block(pkg, reason, detail)
        }, 120)
    }

    private fun handleWindowChange(pkg: String) {
        if (pkg.isBlank()) return
        if (isSafePackage(pkg)) { currentPackage = null; return }
        currentPackage = pkg

        // Temp block
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

        if (aiDetector.cachedAiEnabled && aiDetector.isLegacyAvailable()
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) triggerAiCheckThrottled(pkg)
    }

    private fun handleContentChange(pkg: String) {
        if (pkg.isBlank() || isSafePackage(pkg)) return
        val now = System.currentTimeMillis()
        if (now - lastTextScan < GuardianConstants.TEXT_THROTTLE_MS) return
        lastTextScan = now
        if (!rulesEngine.canBlock(pkg)) return

        serviceScope.launch {
            try {
                val text = withContext(Dispatchers.Default) { collectVisibleText() }
                if (!text.isNullOrBlank()) {
                    val r = rulesEngine.evaluateText(text)
                    if (r is DetectionResult.Block) {
                        // ✅ Fix: performGlobalAction Main thread এ
                        withContext(Dispatchers.Main) {
                            goHomeAndBlock(pkg, r.reason, r.detail)
                        }
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
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        var bmp: Bitmap? = null
                        serviceScope.launch {
                            try {
                                val hw = screenshot.hardwareBuffer
                                val cs = screenshot.colorSpace
                                bmp = Bitmap.wrapHardwareBuffer(hw, cs)
                                    ?.copy(Bitmap.Config.ARGB_8888, false)
                                try { hw.close() } catch (_: Throwable) {}
                                val b = bmp ?: return@launch
                                val gender = aiDetector.cachedUserGender
                                var blocked = false

                                if (gender != "NONE"
                                    && aiDetector.isGenderModelAvailable()
                                    && aiDetector.isNsfwGateAvailable()
                                ) {
                                    if (aiDetector.isOppositeGenderNsfw(b, gender)) {
                                        // ✅ Fix: Main thread এ go home
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
                            } catch (t: Throwable) {
                                Timber.e(t, "AI check failed")
                            } finally {
                                try { bmp?.recycle() } catch (_: Throwable) {}
                            }
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        Timber.w("Screenshot fail: $errorCode")
                    }
                }
            )
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
                    val pkg = currentPackage ?: continue
                    if (isSafePackage(pkg)) continue

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

                    if (!rulesEngine.canBlock(pkg)) continue

                    val r = rulesEngine.evaluatePackage(pkg)
                    if (r is DetectionResult.Block) {
                        withContext(Dispatchers.Main) {
                            goHomeAndBlock(pkg, r.reason, r.detail)
                        }
                        continue
                    }

                    if (aiDetector.cachedAiEnabled && aiDetector.isLegacyAvailable()
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ) {
                        withContext(Dispatchers.Main) {
                            triggerAiCheckThrottled(pkg)
                        }
                    }

                } catch (t: Throwable) { Timber.e(t) }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenReceiver) }
        periodicJob?.cancel()
        // ✅ Fix: scope cancel করো — resource leak বন্ধ
        serviceScope.cancel()
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        try { aiDetector.close() } catch (_: Throwable) {}
    }
}