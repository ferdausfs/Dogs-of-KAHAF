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
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_NONE
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.service.blocker.BlockingEngine
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.RulesEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    companion object {
        private const val TEXT_THROTTLE_MS = 600L
        private const val AI_THROTTLE_MS = 700L
        private const val AI_PERIODIC_MS = 850L
        private const val AI_FOLLOW_UP_MS = 450L
    }

    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var blockingEngine: BlockingEngine
    @Inject lateinit var aiDetector: AiDetector
    @Inject lateinit var prefs: GuardianPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var periodicJob: Job? = null

    @Volatile private var lastForegroundPkg: String? = null
    private var lastTextScanMs = 0L
    private val lastAiScanByPkg = HashMap<String, Long>()
    private val aiInFlight = AtomicBoolean(false)

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

        scope.launch {
            rulesEngine.reload()
            // Try to load both legacy + new pipelines. Failures are silent.
            runCatching {
                if (aiDetector.isModelAvailable()) aiDetector.ensureLoaded()
            }.onFailure { Timber.e(it, "Legacy model preload failed") }
            runCatching {
                if (aiDetector.isNsfwModelAvailable()) aiDetector.ensureGenderPipelineLoaded()
            }.onFailure { Timber.e(it, "Gender pipeline preload failed") }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            rulesReloadReceiver,
            IntentFilter(RulesEngine.ACTION_RULES_CHANGED)
        )
        startPeriodicAiScanner()
    }

    private fun startPeriodicAiScanner() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                delay(AI_PERIODIC_MS)
                val pkg = lastForegroundPkg ?: continue
                if (!rulesEngine.canBlock(pkg)) continue
                val enabled = runCatching { prefs.aiDetectionEnabled.first() }.getOrDefault(false)
                if (!enabled) continue
                if (!aiDetector.isModelAvailable() && !aiDetector.isNsfwModelAvailable()) continue
                triggerAiCheck(pkg)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
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

        when (val result = rulesEngine.evaluatePackage(pkg)) {
            is DetectionResult.Block -> {
                blockingEngine.block(pkg, result.reason, result.detail)
            }

            DetectionResult.Allow -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && rulesEngine.canBlock(pkg)) {
                    triggerAiCheck(pkg, force = true)
                    scheduleFollowUpScan(pkg)
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

    @RequiresApi(Build.VERSION_CODES.R)
    private fun triggerAiCheck(pkg: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val last = lastAiScanByPkg[pkg] ?: 0L
        if (!force && now - last < AI_THROTTLE_MS) return
        lastAiScanByPkg[pkg] = now

        if (!aiInFlight.compareAndSet(false, true)) return

        scope.launch {
            try {
                val aiEnabled = runCatching { prefs.aiDetectionEnabled.first() }.getOrDefault(false)
                if (!aiEnabled) {
                    aiInFlight.set(false)
                    return@launch
                }

                // Read user gender once per scan (cheap — DataStore).
                val userGender = runCatching { prefs.userGender.first() }.getOrDefault(GENDER_NONE)

                // Decide which detectors are even possible right now.
                val genderFeatureOn = userGender != GENDER_NONE && aiDetector.isNsfwModelAvailable()
                val legacyOn        = aiDetector.isModelAvailable()

                if (!genderFeatureOn && !legacyOn) {
                    aiInFlight.set(false)
                    return@launch
                }

                // Eagerly attempt loads — failures are silent inside AiDetector.
                if (genderFeatureOn) aiDetector.ensureGenderPipelineLoaded()
                if (legacyOn)        aiDetector.ensureLoaded()

                if (!rulesEngine.canBlock(pkg)) {
                    aiInFlight.set(false)
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(result: ScreenshotResult) {
                                scope.launch(Dispatchers.Default) {
                                    var bmp: Bitmap? = null
                                    try {
                                        bmp = Bitmap.wrapHardwareBuffer(
                                            result.hardwareBuffer,
                                            result.colorSpace
                                        )?.copy(Bitmap.Config.ARGB_8888, false)
                                        runCatching { result.hardwareBuffer.close() }

                                        val safeBmp = bmp ?: return@launch

                                        // ── Step 1: opposite-gender NSFW (if armed) ──
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

                                        // ── Step 2: legacy generic NSFW fallback ──
                                        if (!blocked && legacyOn) {
                                            val hit = runCatching {
                                                aiDetector.isUnsafe(safeBmp)
                                            }.onFailure {
                                                Timber.e(it, "isUnsafe threw")
                                            }.getOrDefault(false)

                                            if (hit) {
                                                Timber.d("AI flagged content in $pkg")
                                                withContext(Dispatchers.Main) {
                                                    blockingEngine.block(
                                                        pkg,
                                                        BlockReason.AI_DETECTION,
                                                        "AI detected unsafe content"
                                                    )
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
                                Timber.w("takeScreenshot failed: errorCode=$errorCode")
                                aiInFlight.set(false)
                            }
                        }
                    )
                }
            } catch (t: Throwable) {
                Timber.e(t, "triggerAiCheck error")
                aiInFlight.set(false)
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
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            if (node !== root) node.recycle()
            nodes++
        }
        queue.forEach { if (it !== root) it.recycle() }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { periodicJob?.cancel() }
        runCatching {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(rulesReloadReceiver)
        }
        runCatching { scope.cancel() }
        runCatching { aiDetector.close() }
        super.onDestroy()
    }
}
