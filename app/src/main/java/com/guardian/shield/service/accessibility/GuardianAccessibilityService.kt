package com.guardian.shield.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.data.repository.AppRuleRepository
import com.guardian.shield.data.repository.BlockEventRepository
import com.guardian.shield.data.repository.KeywordRepository
import com.guardian.shield.di.AccessibilityServiceEntryPoint
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.service.blocker.BlockingEngine
import com.guardian.shield.service.blocker.GuardianForegroundService
import com.guardian.shield.service.blocker.PreemptiveBlurManager
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.RulesEngine
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val SYSTEM_UI_SKIP = setOf(
    "com.android.systemui",
    "com.google.android.inputmethod.latin",
    "com.samsung.android.honeyboard",
    "com.sec.android.inputmethod",
    "com.touchtype.swiftkey",
    "com.swiftkey.swiftkeyapp"
)

class GuardianAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "Guardian_Service"
        const val ACTION_REFRESH_RULES = "com.guardian.shield.REFRESH_RULES"
        const val ACTION_RELOAD_MODEL = "com.guardian.shield.RELOAD_MODEL"
        private const val TEXT_DEBOUNCE_MS = 200L
        private const val AI_DEBOUNCE_MS = 250L
    }

    private lateinit var rulesEngine: RulesEngine
    private lateinit var blockingEngine: BlockingEngine
    private lateinit var aiDetector: AiDetector
    private lateinit var appRuleRepo: AppRuleRepository
    private lateinit var keywordRepo: KeywordRepository
    private lateinit var blockEventRepo: BlockEventRepository
    private lateinit var prefs: GuardianPreferences
    private var blurManager: PreemptiveBlurManager? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExec = Executor { command -> mainHandler.post(command) }

    @Volatile private var currentForegroundPkg = ""
    @Volatile private var aiEnabled = false
    @Volatile private var aiThreshold = 0.30f
    @Volatile private var aiIntervalMs = 600L
    @Volatile private var aiScanJob: Job? = null
    @Volatile private var isInjected = false

    private val aiBusy = AtomicBoolean(false)
    private val consecutiveSafeFrames = AtomicInteger(0)

    private var textDebounceJob: Job? = null
    private var aiDebounceJob: Job? = null

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (!isInjected) return
            when (intent?.action) {
                ACTION_REFRESH_RULES -> serviceScope.launch {
                    loadRulesIntoEngine()
                    loadSettings()
                }
                ACTION_RELOAD_MODEL -> serviceScope.launch {
                    loadSettings()
                    reloadAiModel()
                }
            }
        }
    }

    override fun onServiceConnected() {
        Timber.d("$TAG onServiceConnected")
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                AccessibilityServiceEntryPoint::class.java
            )
            rulesEngine = entryPoint.rulesEngine()
            blockingEngine = entryPoint.blockingEngine()
            aiDetector = entryPoint.aiDetector()
            appRuleRepo = entryPoint.appRuleRepo()
            keywordRepo = entryPoint.keywordRepo()
            blockEventRepo = entryPoint.blockEventRepo()
            prefs = entryPoint.prefs()
            blurManager = PreemptiveBlurManager(this)
            isInjected = true
        } catch (e: Exception) {
            Timber.e(e, "$TAG injection FAILED")
            return
        }

        serviceScope.launch {
            loadRulesIntoEngine()
            loadSettings()
            blockingEngine.loadSettings()
            if (aiEnabled) reloadAiModel()
        }
        registerReceivers()
        startForegroundWatchdog()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isInjected) return
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (!SYSTEM_UI_SKIP.contains(pkg)) {
                    val pkgChanged = currentForegroundPkg != pkg
                    currentForegroundPkg = pkg

                    if (pkgChanged) {
                        consecutiveSafeFrames.set(0)
                        blurManager?.hideBlur()

                        // FIX: Trigger AI scan for ANY app (not just "risky" list)
                        // because porn can be viewed via ANY browser/gallery/file manager
                        if (aiEnabled && aiDetector.isLoaded() &&
                            !rulesEngine.isWhitelisted(pkg) &&
                            !rulesEngine.isEssentialSystem(pkg) &&
                            pkg != rulesEngine.OUR_PACKAGE_HOLDER() &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                            blurManager?.showBlur(pkg, "Scanning content…")
                            triggerImmediateAiScan()
                        }
                    }
                }
                handleAppEvent(pkg)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                debounceTextScan(pkg)

                // FIX: AI scan on content change for ALL apps
                if (aiEnabled &&
                    aiDetector.isLoaded() &&
                    !rulesEngine.isWhitelisted(pkg) &&
                    !rulesEngine.isEssentialSystem(pkg) &&
                    !blockingEngine.isCoolingDown() &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    debounceAiScan()
                }
            }
        }
    }

    override fun onInterrupt() { Timber.w("$TAG interrupted") }

    override fun onDestroy() {
        stopAiScanLoop()
        blurManager?.destroy()
        serviceScope.cancel()
        try { unregisterReceiver(refreshReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun handleAppEvent(pkg: String) {
        serviceScope.launch(Dispatchers.Default) {
            try {
                when (val r = rulesEngine.evaluateApp(pkg)) {
                    is DetectionResult.Block ->
                        logAndBlock(pkg, getAppName(pkg), r.reason, r.detail)
                    else -> { /* allow */ }
                }
            } catch (e: Exception) { Timber.e(e, "$TAG handleAppEvent") }
        }
    }

    private fun debounceTextScan(pkg: String) {
        textDebounceJob?.cancel()
        textDebounceJob = serviceScope.launch {
            delay(TEXT_DEBOUNCE_MS)
            if (!isInjected) return@launch
            if (rulesEngine.isWhitelisted(pkg)) return@launch
            if (blockingEngine.isCoolingDown()) return@launch

            val root = try { rootInActiveWindow } catch (_: Exception) { null }
                ?: return@launch
            try {
                val text = collectAllText(root)
                if (text.isBlank()) return@launch

                val r = rulesEngine.evaluateText(pkg, text)
                if (r is DetectionResult.Block) {
                    logAndBlock(pkg, getAppName(pkg), r.reason, r.detail)
                }
            } catch (e: Exception) { Timber.e(e, "$TAG textScan") }
            finally { try { root.recycle() } catch (_: Exception) {} }
        }
    }

    private fun debounceAiScan() {
        aiDebounceJob?.cancel()
        aiDebounceJob = serviceScope.launch {
            delay(AI_DEBOUNCE_MS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                triggerImmediateAiScan()
            }
        }
    }

    private suspend fun reloadAiModel() {
        try {
            aiEnabled = prefs.isAiDetectionEnabled.first()
            aiThreshold = prefs.aiThreshold.first()
            aiIntervalMs = prefs.aiIntervalMs.first()
        } catch (e: Exception) { Timber.e(e, "$TAG reloadAi settings") }

        if (!aiEnabled) {
            stopAiScanLoop()
            aiDetector.unload()
            blurManager?.hideBlur()
            return
        }
        if (!AiDetector.isModelAvailable(applicationContext)) {
            Timber.w("$TAG AI enabled but no model file")
            stopAiScanLoop()
            return
        }

        val ok = withContext(Dispatchers.IO) { aiDetector.reload() }
        Timber.d("$TAG AI model reloaded: $ok")
        if (ok && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startAiScanLoop()
        } else {
            stopAiScanLoop()
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun startAiScanLoop() {
        stopAiScanLoop()
        aiScanJob = serviceScope.launch {
            while (isActive) {
                delay(aiIntervalMs)
                if (!aiDetector.isLoaded()) continue
                if (rulesEngine.isWhitelisted(currentForegroundPkg)) continue
                if (blockingEngine.isCoolingDown()) continue
                if (aiBusy.get()) continue
                if (currentForegroundPkg.isBlank()) continue
                if (rulesEngine.isEssentialSystem(currentForegroundPkg)) continue
                captureAndAnalyze()
            }
        }
        Timber.d("$TAG AI scan loop started, interval=${aiIntervalMs}ms")
    }

    private fun stopAiScanLoop() {
        aiScanJob?.cancel()
        aiScanJob = null
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun triggerImmediateAiScan() {
        if (aiBusy.get()) return
        if (!aiDetector.isLoaded()) return
        serviceScope.launch { captureAndAnalyze() }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    @android.annotation.SuppressLint("NewApi")
    private fun captureAndAnalyze() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!aiBusy.compareAndSet(false, true)) return

        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY, mainExec,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        serviceScope.launch(Dispatchers.Default) {
                            analyzeScreenshot(result)
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        aiBusy.set(false)
                        if (errorCode != 2)
                            Timber.w("$TAG screenshot fail code=$errorCode")
                    }
                }
            )
        } catch (e: Exception) {
            aiBusy.set(false)
            Timber.e(e, "$TAG captureAndAnalyze")
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    @android.annotation.SuppressLint("NewApi")
    private suspend fun analyzeScreenshot(result: ScreenshotResult) {
        var full: Bitmap? = null
        var cropped: Bitmap? = null
        try {
            val hb = result.hardwareBuffer ?: run { aiBusy.set(false); return }

            val hwBmp = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
            full = hwBmp?.copy(Bitmap.Config.ARGB_8888, false)
            hwBmp?.recycle()

            if (full == null) { aiBusy.set(false); return }

            val w = full.width
            val h = full.height
            // FIX: Smaller crop - keep more of the image for better detection
            val topCut = (h * 0.05f).toInt()
            val botCut = (h * 0.05f).toInt()
            val cropH = h - topCut - botCut
            cropped = if (cropH > 100)
                Bitmap.createBitmap(full, 0, topCut, w, cropH)
            else full

            if (aiDetector.shouldSkipFrame(cropped)) {
                Timber.d("$TAG skipping uniform/dark frame")
                onSafeFrame()
                return
            }

            val r = aiDetector.classify(cropped, aiThreshold)
            val pkg = currentForegroundPkg
            Timber.i("$TAG AI [$pkg]: ${r.label} score=${r.unsafeScore}")

            if (r.isUnsafe) {
                consecutiveSafeFrames.set(0)
                val ev = rulesEngine.evaluateAiResult(pkg, r.unsafeScore, aiThreshold)
                if (ev is DetectionResult.Block) {
                    logAndBlock(pkg, getAppName(pkg), ev.reason, ev.detail)
                }
            } else {
                onSafeFrame()
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG analyzeScreenshot")
        } finally {
            try { result.hardwareBuffer?.close() } catch (_: Exception) {}
            if (cropped != null && cropped !== full) cropped.recycle()
            full?.recycle()
            aiBusy.set(false)
        }
    }

    private fun onSafeFrame() {
        if (consecutiveSafeFrames.incrementAndGet() >= 2) {
            blurManager?.hideBlur()
        }
    }

    private suspend fun logAndBlock(
        pkg: String, appName: String, reason: BlockReason, detail: String
    ) {
        try {
            withContext(Dispatchers.IO) {
                blockEventRepo.logEvent(
                    BlockEvent(
                        packageName = pkg,
                        appName = appName,
                        reason = reason,
                        detail = detail
                    )
                )
            }
        } catch (e: Exception) { Timber.e(e, "$TAG log") }

        withContext(Dispatchers.Main) {
            blockingEngine.executeBlock(
                this@GuardianAccessibilityService,
                pkg, appName, reason, detail
            )
        }
    }

    private suspend fun loadRulesIntoEngine() {
        try {
            rulesEngine.refreshBlockedApps(appRuleRepo.getBlockedPackages())
            rulesEngine.refreshWhitelistedApps(appRuleRepo.getWhitelistedPackages())
            rulesEngine.refreshKeywords(keywordRepo.getActiveKeywords())
        } catch (e: Exception) { Timber.e(e, "$TAG loadRules") }
    }

    private suspend fun loadSettings() {
        try {
            aiEnabled = prefs.isAiDetectionEnabled.first()
            aiThreshold = prefs.aiThreshold.first()
            aiIntervalMs = prefs.aiIntervalMs.first()
            rulesEngine.setProtectionEnabled(prefs.isProtectionEnabled.first())
            rulesEngine.setKeywordDetectionEnabled(prefs.isKeywordDetectionEnabled.first())
            rulesEngine.setStrictMode(prefs.isStrictMode.first())
            Timber.d("$TAG settings: ai=$aiEnabled, threshold=$aiThreshold")
        } catch (e: Exception) { Timber.e(e, "$TAG loadSettings") }
    }

    private fun getAppName(pkg: String): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(pkg, 0)
        ).toString()
    } catch (_: Exception) { pkg }

    private fun collectAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder(512)
        var nodeCount = 0
        val maxNodes = 200

        fun go(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 15 || nodeCount >= maxNodes) return
            nodeCount++
            n.text?.let { if (it.length < 500) sb.append(it).append(' ') }
            n.contentDescription?.let { if (it.length < 200) sb.append(it).append(' ') }

            val cc = minOf(n.childCount, 50)
            for (i in 0 until cc) {
                if (nodeCount >= maxNodes) break
                val c = try { n.getChild(i) } catch (_: Exception) { null } ?: continue
                go(c, depth + 1)
                try { c.recycle() } catch (_: Exception) {}
            }
        }
        go(node, 0)
        return sb.toString()
    }

    private fun startForegroundWatchdog() {
        try {
            val intent = Intent(this, GuardianForegroundService::class.java)
            startForegroundService(intent)
        } catch (e: Exception) { Timber.e(e, "$TAG fgService") }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(ACTION_REFRESH_RULES)
            addAction(ACTION_RELOAD_MODEL)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                registerReceiver(refreshReceiver, filter, RECEIVER_NOT_EXPORTED)
            else
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(refreshReceiver, filter)
        } catch (e: Exception) { Timber.e(e, "$TAG regReceiver") }
    }
}