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
import com.guardian.shield.util.ScanBudgetPolicy
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var isScreenOn = true
    @Volatile private var isBatteryLow = false
    @Volatile private var isCharging = false
    @Volatile private var protectionEnabled = true
    @Volatile private var currentPackage: String? = null
    @Volatile private var lastTextScan = 0L
    @Volatile private var lastInteractionAt = 0L
    @Volatile private var isBlockingInProgress = false

    private val aiScanMap = linkedMapOf<String, Long>()
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
                    isBlockingInProgress = false
                }
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_LOW -> isBatteryLow = true
                Intent.ACTION_BATTERY_OKAY -> isBatteryLow = false
                Intent.ACTION_POWER_CONNECTED -> isCharging = true
                Intent.ACTION_POWER_DISCONNECTED -> isCharging = false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("Accessibility connected")
        homePkg = AppClassifier.getHomePkg(this)
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        lastInteractionAt = System.currentTimeMillis()

        registerScreenReceiver()
        registerBatteryReceiver()
        hydrateBatteryState()
        isScreenOn = (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true

        ioScope.launch {
            try {
                prefs.rulesVersion.collect {
                    try {
                        rulesEngine.reload()
                        aiDetector.ensureLoaded()
                    } catch (t: Throwable) {
                        Timber.e(t)
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }

        serviceScope.launch {
            try {
                prefs.protectionEnabled.collect { protectionEnabled = it }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }

        aiDetector.startPrefsCache(serviceScope)
        startPeriodicScanner()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        if (!protectionEnabled || isDeviceLocked()) return

        try {
            val pkg = ev.packageName?.toString().orEmpty()
            if (pkg.isNotBlank()) lastInteractionAt = System.currentTimeMillis()

            if (ev.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
                && pkg.isNotBlank()
                && reelScrollDetector.REEL_PACKAGES.contains(pkg)
            ) {
                if (reelScrollDetector.recordScroll(pkg)) {
                    reelScrollDetector.markReminderShown(pkg)
                    val ctx: Context = this
                    mainHandler.post {
                        runCatching {
                            ctx.startActivity(ReelReminderActivity.createIntent(ctx, pkg))
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
        } catch (t: Throwable) {
            Timber.e(t)
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        }
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(batteryReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(batteryReceiver, filter)
            }
        }
    }

    private fun hydrateBatteryState() {
        runCatching {
            val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = sticky?.getIntExtra("status", -1) ?: -1
            isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
            val level = sticky?.getIntExtra("level", -1) ?: -1
            val scale = sticky?.getIntExtra("scale", -1) ?: -1
            val percent = if (level >= 0 && scale > 0) (level * 100f / scale) else 100f
            isBatteryLow = percent <= 15f
        }
    }

    private fun isDeviceLocked(): Boolean = try {
        keyguardManager?.isKeyguardLocked == true
    } catch (_: Throwable) {
        false
    }

    private fun isSafePackage(pkg: String): Boolean = AppClassifier.isAlwaysAllowedPackage(
        packageName,
        pkg,
        rulesEngine.current().inputMethods,
        homePkg
    )

    private fun goHomeAndBlock(pkg: String, reason: BlockReason, detail: String) {
        if (isBlockingInProgress) {
            Timber.d("Block in progress, skip: $pkg")
            return
        }
        isBlockingInProgress = true
        currentPackage = null
        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.postDelayed({
            blockingEngine.block(pkg, reason, detail)
        }, 120)
    }

    private fun handleWindowChange(pkg: String) {
        if (pkg.isBlank()) return

        val previous = currentPackage
        if (previous != null && previous != pkg && reelScrollDetector.REEL_PACKAGES.contains(previous)) {
            reelScrollDetector.resetSession(previous)
        }

        if (isSafePackage(pkg)) {
            currentPackage = null
            isBlockingInProgress = false
            reelScrollDetector.resetSession(pkg)
            return
        }

        if (!rulesEngine.canBlock(pkg)) {
            currentPackage = pkg
            isBlockingInProgress = false
            return
        }

        currentPackage = pkg
        lastInteractionAt = System.currentTimeMillis()

        val tempBlock = blockingEngine.isTempBlocked(pkg)
        if (tempBlock != null) {
            goHomeAndBlock(pkg, BlockReason.APP_BLOCKED, "temp_block:${tempBlock.remainingMinutes}min")
            return
        }

        val result = rulesEngine.evaluatePackage(pkg)
        if (result is DetectionResult.Block) {
            goHomeAndBlock(pkg, result.reason, result.detail)
            return
        }

        isBlockingInProgress = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && aiDetector.cachedAiEnabled && aiDetector.isLegacyAvailable()) {
            maybeRunAiScan(pkg)
        }
    }

    private fun handleContentChange(pkg: String) {
        if (!ScanBudgetPolicy.shouldRunTextScan(
                packageName = pkg,
                isSafePackage = isSafePackage(pkg),
                isBlockingInProgress = isBlockingInProgress,
                lastTextScanAt = lastTextScan,
                now = System.currentTimeMillis(),
                throttleMs = GuardianConstants.TEXT_THROTTLE_MS
            )
        ) return

        lastTextScan = System.currentTimeMillis()
        lastInteractionAt = lastTextScan

        serviceScope.launch {
            try {
                val text = withContext(Dispatchers.Default) { collectVisibleText() }
                if (!text.isNullOrBlank()) {
                    val result = rulesEngine.evaluateText(text)
                    if (result is DetectionResult.Block) {
                        withContext(Dispatchers.Main) {
                            goHomeAndBlock(pkg, result.reason, result.detail)
                        }
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    private fun collectVisibleText(): String? {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        val builder = StringBuilder()
        val visited = HashSet<Int>()
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < GuardianConstants.MAX_NODES_BFS) {
            val node = queue.removeFirst()
            try {
                val identity = System.identityHashCode(node)
                if (!visited.add(identity)) continue
                count++
                node.text?.toString()?.takeIf { it.isNotBlank() }?.let { builder.append(it).append(' ') }
                node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { builder.append(it).append(' ') }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            } finally {
                runCatching { node.recycle() }
            }
        }

        return builder.toString().trim().ifEmpty { null }
    }

    private fun maybeRunAiScan(pkg: String) {
        if (!ScanBudgetPolicy.shouldRunHeavyScan(
                packageName = pkg,
                isSafePackage = isSafePackage(pkg),
                isScreenOn = isScreenOn,
                protectionEnabled = protectionEnabled,
                isBlockingInProgress = isBlockingInProgress,
                isBatteryLow = isBatteryLow,
                isCharging = isCharging,
                lastInteractionAt = lastInteractionAt,
                now = System.currentTimeMillis()
            )
        ) return

        triggerAiCheckThrottled(pkg)
    }

    private fun triggerAiCheckThrottled(pkg: String) {
        if (isBlockingInProgress) return
        val now = System.currentTimeMillis()
        synchronized(aiScanMap) {
            val last = aiScanMap[pkg] ?: 0L
            if (now - last < GuardianConstants.AI_THROTTLE_MS) return
            aiScanMap[pkg] = now
            while (aiScanMap.size > GuardianConstants.MAX_AI_SCAN_MAP) {
                aiScanMap.entries.iterator().run {
                    if (hasNext()) {
                        next()
                        remove()
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) triggerAiCheck(pkg)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun triggerAiCheck(pkg: String) {
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    var bmp: Bitmap? = null
                    serviceScope.launch {
                        try {
                            if (isBlockingInProgress) return@launch
                            val hw = screenshot.hardwareBuffer
                            val cs = screenshot.colorSpace
                            bmp = Bitmap.wrapHardwareBuffer(hw, cs)?.copy(Bitmap.Config.ARGB_8888, false)
                            try { hw.close() } catch (_: Throwable) {}
                            val bitmap = bmp ?: return@launch
                            if (!rulesEngine.canBlock(pkg)) return@launch

                            val gender = aiDetector.cachedUserGender
                            var blocked = false

                            if (gender != "NONE"
                                && aiDetector.isGenderModelAvailable()
                                && aiDetector.isNsfwGateAvailable()
                            ) {
                                if (aiDetector.isOppositeGenderNsfw(bitmap, gender)) {
                                    withContext(Dispatchers.Main) {
                                        goHomeAndBlock(pkg, BlockReason.AI_DETECTION, "gender-nsfw")
                                    }
                                    blocked = true
                                }
                            }
                            if (!blocked && aiDetector.isLegacyAvailable()) {
                                if (aiDetector.isUnsafe(bitmap)) {
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
            })
        } catch (t: Throwable) {
            Timber.e(t)
        }
    }

    private fun startPeriodicScanner() {
        periodicJob?.cancel()
        periodicJob = serviceScope.launch {
            while (isActive) {
                try {
                    delay(if (!isScreenOn) GuardianConstants.SCREEN_OFF_PERIODIC_MS else GuardianConstants.AI_PERIODIC_MS)

                    if (!isScreenOn || !protectionEnabled || isDeviceLocked() || isBlockingInProgress) continue

                    val pkg = currentPackage ?: continue
                    if (isSafePackage(pkg) || !rulesEngine.canBlock(pkg)) continue

                    val tempBlock = blockingEngine.isTempBlocked(pkg)
                    if (tempBlock != null) {
                        withContext(Dispatchers.Main) {
                            goHomeAndBlock(pkg, BlockReason.APP_BLOCKED, "temp_block:${tempBlock.remainingMinutes}min")
                        }
                        continue
                    }

                    val result = rulesEngine.evaluatePackage(pkg)
                    if (result is DetectionResult.Block) {
                        withContext(Dispatchers.Main) { goHomeAndBlock(pkg, result.reason, result.detail) }
                        continue
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && aiDetector.cachedAiEnabled && aiDetector.isLegacyAvailable()) {
                        withContext(Dispatchers.Main) { maybeRunAiScan(pkg) }
                    }
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(batteryReceiver) }
        periodicJob?.cancel()
        serviceScope.cancel()
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        try { aiDetector.close() } catch (_: Throwable) {}
    }
}
