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
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.data.repository.AppRuleRepository
import com.guardian.shield.data.repository.BlockEventRepository
import com.guardian.shield.data.repository.KeywordRepository
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.service.blocker.BlockingEngine
import com.guardian.shield.service.blocker.GuardianForegroundService
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.RulesEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "Guardian_Service"
        const val ACTION_REFRESH_RULES = "com.guardian.shield.REFRESH_RULES"
        const val ACTION_RELOAD_MODEL  = "com.guardian.shield.RELOAD_MODEL"

        // Keyword debounce — don't scan text on every keystroke
        private const val TEXT_DEBOUNCE_MS = 700L

        // Supported browser packages for URL bar detection
        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome", "org.mozilla.firefox", "com.opera.browser",
            "com.brave.browser", "com.microsoft.emmx", "com.UCMobile.intl",
            "com.sec.android.app.sbrowser", "com.kiwibrowser.browser"
        )
    }

    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var blockingEngine: BlockingEngine
    @Inject lateinit var aiDetector: AiDetector
    @Inject lateinit var appRuleRepo: AppRuleRepository
    @Inject lateinit var keywordRepo: KeywordRepository
    @Inject lateinit var blockEventRepo: BlockEventRepository
    @Inject lateinit var prefs: GuardianPreferences

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile private var currentForegroundPkg = ""
    @Volatile private var aiEnabled = false
    @Volatile private var aiThreshold = 0.40f
    @Volatile private var aiIntervalMs = 2_500L
    @Volatile private var aiScanJob: Job? = null
    @Volatile private var aiBusy = false

    private var textDebounceJob: Job? = null

    // ── Receivers ─────────────────────────────────────────────────────

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_REFRESH_RULES -> serviceScope.launch { loadRulesIntoEngine() }
                ACTION_RELOAD_MODEL  -> serviceScope.launch { loadAiModel() }
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    override fun onServiceConnected() {
        Timber.d("$TAG onServiceConnected")
        serviceScope.launch {
            loadRulesIntoEngine()
            loadSettings()
            if (aiEnabled) loadAiModel()
        }
        registerReceivers()
        startForegroundWatchdog()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        // Track foreground app (ignore system UI transitions)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (!SYSTEM_UI_SKIP.contains(pkg) && !rulesEngine.isSystemUi(pkg)) {
                currentForegroundPkg = pkg
            }
        }

        // ── App-level check (every window state change) ───────────────
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleAppEvent(pkg, event)
        }

        // ── Keyword check (text content events, debounced) ────────────
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            debounceTextScan(pkg)
        }
    }

    override fun onInterrupt() {
        Timber.w("$TAG interrupted")
    }

    override fun onDestroy() {
        stopAiScanLoop()
        serviceScope.cancel()
        try { unregisterReceiver(refreshReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    // ── App event handler ──────────────────────────────────────────────

    private fun handleAppEvent(pkg: String, event: AccessibilityEvent) {
        serviceScope.launch(Dispatchers.Default) {
            when (val result = rulesEngine.evaluateApp(pkg)) {
                is DetectionResult.Block -> {
                    val appName = getAppName(pkg)
                    logAndBlock(pkg, appName, result.reason, result.detail, event)
                }
                else -> { /* allow */ }
            }
        }
    }

    // ── Text / keyword scan ────────────────────────────────────────────

    private fun debounceTextScan(pkg: String) {
        textDebounceJob?.cancel()
        textDebounceJob = serviceScope.launch {
            delay(TEXT_DEBOUNCE_MS)
            if (rulesEngine.isWhitelisted(pkg)) return@launch
            if (blockingEngine.isCoolingDown()) return@launch

            val root = rootInActiveWindow ?: return@launch
            try {
                val text = collectAllText(root)
                if (text.isBlank()) return@launch

                // Also check URL bar for browsers
                val scanText = if (pkg in BROWSER_PACKAGES) {
                    val url = getUrlBarText(root)
                    if (url != null) "$text $url" else text
                } else text

                val result = rulesEngine.evaluateText(pkg, scanText)
                if (result is DetectionResult.Block) {
                    val appName = getAppName(pkg)
                    logAndBlock(pkg, appName, result.reason, result.detail, null)
                }
            } finally {
                root.recycle()
            }
        }
    }

    // ── AI scan loop (event-triggered, interval-gated) ────────────────

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.R)
    private fun startAiScanLoop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        stopAiScanLoop()
        aiScanJob = serviceScope.launch {
            while (isActive) {
                delay(aiIntervalMs)
                if (!aiDetector.isLoaded()) continue
                if (rulesEngine.isWhitelisted(currentForegroundPkg)) continue
                if (aiBusy) continue
                captureAndAnalyze()
            }
        }
        Timber.d("$TAG AI scan loop started (interval=${aiIntervalMs}ms)")
    }

    private fun stopAiScanLoop() {
        aiScanJob?.cancel()
        aiScanJob = null
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.R)
    @android.annotation.SuppressLint("NewApi")
    private fun captureAndAnalyze() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        aiBusy = true
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        serviceScope.launch(Dispatchers.Default) {
                            analyzeScreenshot(result)
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        aiBusy = false
                        if (errorCode != 2) Timber.w("$TAG screenshot failed: $errorCode")
                    }
                }
            )
        } catch (e: Exception) {
            aiBusy = false
            Timber.e(e, "$TAG captureAndAnalyze error")
        }
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.R)
    @android.annotation.SuppressLint("NewApi")
    private suspend fun analyzeScreenshot(result: ScreenshotResult) {
        var full: Bitmap? = null
        var cropped: Bitmap? = null
        try {
            val hb = result.hardwareBuffer ?: return
            val hwBmp = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
            full = hwBmp?.copy(Bitmap.Config.ARGB_8888, false)
            hwBmp?.recycle()
            full ?: return

            val w = full.width; val h = full.height
            val topCut = (h * 0.07f).toInt()
            val botCut = (h * 0.09f).toInt()
            val cropH = h - topCut - botCut
            cropped = if (cropH > 100) Bitmap.createBitmap(full, 0, topCut, w, cropH) else full

            if (aiDetector.shouldSkipFrame(cropped)) return

            val aiResult = aiDetector.classify(cropped, aiThreshold)
            val pkg = currentForegroundPkg

            val evalResult = rulesEngine.evaluateAiResult(pkg, aiResult.unsafeScore, aiThreshold)
            if (evalResult is DetectionResult.Block) {
                val appName = getAppName(pkg)
                withContext(Dispatchers.Main) {
                    logAndBlock(pkg, appName, evalResult.reason, evalResult.detail, null)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG analyzeScreenshot error")
        } finally {
            try { result.hardwareBuffer?.close() } catch (_: Exception) {}
            if (cropped !== full) cropped?.recycle()
            full?.recycle()
            aiBusy = false
        }
    }

    // ── Block + log ────────────────────────────────────────────────────

    private suspend fun logAndBlock(
        pkg: String,
        appName: String,
        reason: BlockReason,
        detail: String,
        event: AccessibilityEvent?
    ) {
        // Log to DB (non-blocking)
        serviceScope.launch(Dispatchers.IO) {
            try {
                blockEventRepo.logEvent(
                    BlockEvent(
                        packageName = pkg,
                        appName     = appName,
                        reason      = reason,
                        detail      = detail
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "$TAG log event failed")
            }
        }

        // Execute block on main thread
        withContext(Dispatchers.Main) {
            blockingEngine.executeBlock(this@GuardianAccessibilityService, pkg, appName, reason, detail)
        }
    }

    // ── Rule cache loading ─────────────────────────────────────────────

    private suspend fun loadRulesIntoEngine() {
        try {
            rulesEngine.refreshBlockedApps(appRuleRepo.getBlockedPackages())
            rulesEngine.refreshWhitelistedApps(appRuleRepo.getWhitelistedPackages())
            rulesEngine.refreshKeywords(keywordRepo.getActiveKeywords())
            Timber.d("$TAG rules loaded into engine")
        } catch (e: Exception) {
            Timber.e(e, "$TAG loadRulesIntoEngine failed")
        }
    }

    private suspend fun loadSettings() {
        try {
            aiEnabled  = prefs.isAiDetectionEnabled.first()
            aiThreshold = prefs.aiThreshold.first()
            aiIntervalMs = prefs.aiIntervalMs.first()
            val protectionOn      = prefs.isProtectionEnabled.first()
            val keywordOn         = prefs.isKeywordDetectionEnabled.first()
            val strictMode        = prefs.isStrictMode.first()
            rulesEngine.setProtectionEnabled(protectionOn)
            rulesEngine.setKeywordDetectionEnabled(keywordOn)
            rulesEngine.setStrictMode(strictMode)
        } catch (e: Exception) {
            Timber.e(e, "$TAG loadSettings failed")
        }
    }

    private suspend fun loadAiModel() {
        if (!aiEnabled) return
        val ok = withContext(Dispatchers.IO) { aiDetector.load() }
        if (ok) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) startAiScanLoop()
            Timber.d("$TAG AI model loaded")
        } else {
            Timber.w("$TAG AI model not available")
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun getAppName(pkg: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (_: Exception) { pkg }
    }

    private fun collectAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun go(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 20) return
            n.text?.let { sb.append(it).append(' ') }
            n.contentDescription?.let { sb.append(it).append(' ') }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                go(c, depth + 1); c.recycle()
            }
        }
        go(node, 0)
        return sb.toString()
    }

    private fun getUrlBarText(root: AccessibilityNodeInfo): String? {
        val ids = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.brave.browser:id/url_bar"
        )
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                val text = nodes[0].text?.toString()
                nodes.forEach { it.recycle() }
                if (text != null) return text
            }
        }
        return null
    }

    private fun startForegroundWatchdog() {
        try {
            val intent = Intent(this, GuardianForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(intent)
            else
                startService(intent)
        } catch (e: Exception) {
            Timber.e(e, "$TAG failed to start foreground service")
        }
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
        } catch (e: Exception) {
            Timber.e(e, "$TAG registerReceivers failed")
        }
    }
}

// System packages that should not update currentForegroundPkg
private val SYSTEM_UI_SKIP = setOf(
    "android", "com.android.systemui",
    "com.google.android.inputmethod.latin",
    "com.samsung.android.honeyboard",
    "com.sec.android.inputmethod",
    "com.touchtype.swiftkey",
    "com.swiftkey.swiftkeyapp"
)
