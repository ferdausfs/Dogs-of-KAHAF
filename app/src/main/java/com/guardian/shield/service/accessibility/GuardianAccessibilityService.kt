package com.guardian.shield.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.service.blocker.BlockingEngine
import com.guardian.shield.service.detection.RulesEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var blockingEngine: BlockingEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastForegroundPkg: String? = null
    private var lastTextScanMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("GuardianAccessibilityService connected")
        scope.launch { rulesEngine.reload() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowChange(pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleContentChange(pkg, event)
        }
    }

    private fun handleWindowChange(pkg: String) {
        if (pkg == lastForegroundPkg) return
        lastForegroundPkg = pkg
        when (val result = rulesEngine.evaluatePackage(pkg)) {
            is DetectionResult.Block -> blockingEngine.block(pkg, result.reason, result.detail)
            DetectionResult.Allow -> Unit
        }
    }

    private fun handleContentChange(pkg: String, event: AccessibilityEvent) {
        if (rulesEngine.isWhitelisted(pkg)) return
        if (pkg == packageName) return
        val now = System.currentTimeMillis()
        if (now - lastTextScanMs < 600) return
        lastTextScanMs = now
        scope.launch {
            val text = collectVisibleText(rootInActiveWindow) ?: return@launch
            when (val result = rulesEngine.evaluateText(text)) {
                is DetectionResult.Block -> withContext(Dispatchers.Main) {
                    blockingEngine.block(pkg, BlockReason.KEYWORD_MATCH, result.detail)
                }
                else -> Unit
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
            nodes++
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
