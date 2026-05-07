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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.service.blocker.BlockingEngine
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.RulesEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var blockingEngine: BlockingEngine
    @Inject lateinit var aiDetector: AiDetector          // FIX: inject AI detector
    @Inject lateinit var prefs: GuardianPreferences      // FIX: inject prefs to check AI toggle

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastForegroundPkg: String? = null
    private var lastTextScanMs = 0L
    private var lastAiScanMs   = 0L  // throttle AI checks to once every 3 s

    // FIX: listen for rule/keyword changes so cache stays fresh
    private val rulesReloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            scope.launch {
                rulesEngine.reload()
                Timber.d("RulesEngine reloaded via broadcast")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("GuardianAccessibilityService connected")
        scope.launch { rulesEngine.reload() }
        LocalBroadcastManager.getInstance(this).registerReceiver(
            rulesReloadReceiver, IntentFilter(RulesEngine.ACTION_RULES_CHANGED)
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED  -> handleWindowChange(pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED     -> handleContentChange(pkg, event)
        }
    }

    private fun handleWindowChange(pkg: String) {
        if (pkg == lastForegroundPkg) return
        lastForegroundPkg = pkg

        when (val result = rulesEngine.evaluatePackage(pkg)) {
            is DetectionResult.Block -> blockingEngine.block(pkg, result.reason, result.detail)
            DetectionResult.Allow -> {
                // FIX: only run AI check on packages that are actually blockable
                // (skip system UI, own package, and whitelisted apps)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && rulesEngine.canBlock(pkg)) {
                    triggerAiCheck(pkg)
                }
            }
        }
    }

    private fun handleContentChange(pkg: String, event: AccessibilityEvent) {
        // FIX: use canBlock() — covers whitelisted, system UI, and own package in one call
        if (!rulesEngine.canBlock(pkg)) return

        val now = System.currentTimeMillis()
        if (now - lastTextScanMs < 600) return
        lastTextScanMs = now

        scope.launch {
            val text = collectVisibleText(rootInActiveWindow) ?: return@launch
            when (val result = rulesEngine.evaluateText(text)) {
                is DetectionResult.Block -> withContext(Dispatchers.Main) {
                    blockingEngine.block(pkg, BlockReason.KEYWORD_MATCH, result.detail)
                }
                DetectionResult.Allow -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val aiNow = System.currentTimeMillis()
                        if (aiNow - lastAiScanMs >= 3_000L) {
                            lastAiScanMs = aiNow
                            withContext(Dispatchers.Main) { triggerAiCheck(pkg) }
                        }
                    }
                }
            }
        }
    }

    /**
     * Takes a screenshot and runs the TFLite model on it.
     * Only called on API 30+ (Android 11+).
     * Requires android:canTakeScreenshot="true" in accessibility_service_config.xml.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun triggerAiCheck(pkg: String) {
        scope.launch {
            val aiEnabled = prefs.aiDetectionEnabled.first()
            if (!aiEnabled) return@launch
            if (!aiDetector.ensureLoaded()) {
                Timber.w("AI model not loaded — skipping screenshot check")
                return@launch
            }
            if (rulesEngine.isWhitelisted(pkg)) return@launch

            // takeScreenshot must be called on the main thread
            withContext(Dispatchers.Main) {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            scope.launch(Dispatchers.Default) {
                                try {
                                    // Copy from HardwareBuffer → ARGB_8888 for TFLite
                                    val bmp = Bitmap.wrapHardwareBuffer(
                                        result.hardwareBuffer, result.colorSpace
                                    )?.copy(Bitmap.Config.ARGB_8888, false)
                                    result.hardwareBuffer.close()

                                    if (bmp == null) return@launch

                                    if (aiDetector.isUnsafe(bmp)) {
                                        bmp.recycle()
                                        Timber.d("AI flagged content in $pkg")
                                        blockingEngine.block(
                                            pkg, BlockReason.AI_DETECTION, "AI detected unsafe content"
                                        )
                                    } else {
                                        bmp.recycle()
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "AI screenshot processing error")
                                }
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Timber.w("takeScreenshot failed: errorCode=$errorCode")
                        }
                    }
                )
            }
        }
    }

    private fun collectVisibleText(root: AccessibilityNodeInfo?): String? {
        root ?: return null
        val sb = StringBuilder()
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.add(root)
        var nodes = 0
        while (queue.isNotEmpty() && nodes < 250) {
            val node = queue.removeFirst()
            node.text?.let { sb.append(it).append(' ') }
            node.contentDescription?.let { sb.append(it).append(' ') }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
            // FIX: recycle every node after we're done with it to avoid memory pressure
            if (node !== root) node.recycle()
            nodes++
        }
        // Drain any remaining un-visited nodes and recycle them
        queue.forEach { if (it !== root) it.recycle() }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(rulesReloadReceiver)
        scope.cancel()
        aiDetector.close()
        super.onDestroy()
    }
}
