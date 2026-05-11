package com.guardian.shield.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
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
import com.guardian.shield.util.GuardianConstants
import com.guardian.shield.util.Scopes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val serviceScope: CoroutineScope = Scopes.default()
    private val ioScope: CoroutineScope = Scopes.io()

    @Volatile private var isScreenOn: Boolean = true
    @Volatile private var protectionEnabled: Boolean = true
    @Volatile private var currentPackage: String? = null
    @Volatile private var lastTextScan: Long = 0L
    private val aiScanMap = LinkedHashMap<String, Long>()
    private var periodicJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> isScreenOn = true
                Intent.ACTION_SCREEN_OFF -> isScreenOn = false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("Accessibility connected")

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
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenOn = pm?.isInteractive ?: true

        // Rules version change হলে reload
        ioScope.launch {
            try {
                prefs.rulesVersion.collect {
                    try {
                        rulesEngine.reload()
                        Timber.d("Rules reloaded (version=$it)")
                    } catch (t: Throwable) {
                        Timber.e(t, "Rules reload failed")
                    }
                }
            } catch (t: Throwable) { Timber.e(t) }
        }

        serviceScope.launch {
            try {
                prefs.protectionEnabled.collect { protectionEnabled = it }
            } catch (t: Throwable) { Timber.e(t) }
        }

        aiDetector.startPrefsCache(serviceScope)

        // Model load — rules version change এ re-try করো (import এর পরে)
        ioScope.launch {
            try {
                prefs.rulesVersion.collect {
                    try { aiDetector.ensureLoaded() } catch (t: Throwable) { Timber.e(t) }
                }
            } catch (t: Throwable) { Timber.e(t) }
        }

        startPeriodicScanner()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        if (!protectionEnabled) return
        try {
            val pkg = ev.packageName?.toString().orEmpty()
            when (ev.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowChange(pkg)
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleContentChange(pkg)
                else -> Unit
            }
        } catch (t: Throwable) {
            Timber.e(t, "onAccessibilityEvent error")
        }
    }

    private fun handleWindowChange(pkg: String) {
        if (pkg.isBlank()) return
        currentPackage = pkg
        val result = rulesEngine.evaluatePackage(pkg)
        if (result is DetectionResult.Block) {
            blockingEngine.block(pkg, result.reason, result.detail)
            return
        }
        if (rulesEngine.canBlock(pkg) && aiDetector.cachedAiEnabled
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            triggerAiCheckThrottled(pkg)
        }
    }

    private fun handleContentChange(pkg: String) {
        if (pkg.isBlank()) return
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
                        blockingEngine.block(pkg, r.reason, r.detail)
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "content change error")
            }
        }
    }

    private fun collectVisibleText(): String? {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        val builder = StringBuilder()
        val visited = HashSet<AccessibilityNodeInfo>()
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.add(root)
        var count = 0
        try {
            while (queue.isNotEmpty() && count < GuardianConstants.MAX_NODES_BFS) {
                val node = queue.poll() ?: continue
                if (!visited.add(node)) continue
                count++
                val txt = node.text?.toString()
                if (!txt.isNullOrBlank()) builder.append(txt).append(' ')
                val desc = node.contentDescription?.toString()
                if (!desc.isNullOrBlank()) builder.append(desc).append(' ')
                for (i in 0 until node.childCount) {
                    val c = node.getChild(i) ?: continue
                    queue.add(c)
                }
            }
        } finally { }
        val s = builder.toString().trim()
        return s.ifEmpty { null }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            triggerAiCheck(pkg)
        }
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
                                if (gender != "NONE" && aiDetector.isGenderModelAvailable()
                                    && aiDetector.isNsfwGateAvailable()
                                ) {
                                    if (aiDetector.isOppositeGenderNsfw(b, gender)) {
                                        blockingEngine.block(pkg, BlockReason.AI_DETECTION, "gender-nsfw")
                                        blocked = true
                                    }
                                }
                                if (!blocked && aiDetector.isLegacyAvailable()) {
                                    if (aiDetector.isUnsafe(b)) {
                                        blockingEngine.block(pkg, BlockReason.AI_DETECTION, "legacy")
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
        } catch (t: Throwable) {
            Timber.e(t, "takeScreenshot threw")
        }
    }

    private fun startPeriodicScanner() {
        periodicJob?.cancel()
        periodicJob = serviceScope.launch {
            while (isActive) {
                try {
                    val delayMs = if (!isScreenOn) GuardianConstants.SCREEN_OFF_PERIODIC_MS
                    else GuardianConstants.AI_PERIODIC_MS
                    delay(delayMs)
                    if (!isScreenOn || !protectionEnabled) continue
                    val pkg = currentPackage ?: continue
                    if (!rulesEngine.canBlock(pkg)) continue

                    // Package rules check
                    val r = rulesEngine.evaluatePackage(pkg)
                    if (r is DetectionResult.Block) {
                        blockingEngine.block(pkg, r.reason, r.detail)
                        continue
                    }

                    // ✅ FIX: Periodic AI scan — এটাই আগে ছিল না
                    // User same app এ থেকে scroll করলেও AI check হবে
                    if (aiDetector.cachedAiEnabled
                        && aiDetector.isLegacyAvailable()
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ) {
                        triggerAiCheckThrottled(pkg)
                    }
                } catch (t: Throwable) {
                    Timber.e(t, "Periodic scanner error")
                }
            }
        }
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenReceiver) }
        periodicJob?.cancel()
        try { aiDetector.close() } catch (_: Throwable) {}
    }
}