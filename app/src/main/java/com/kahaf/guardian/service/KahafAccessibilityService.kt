package com.kahaf.guardian.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.kahaf.guardian.engine.detection.DetectionOrchestrator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KahafAccessibilityService : AccessibilityService() {
    @Inject lateinit var orchestrator: DetectionOrchestrator
    companion object { @Volatile var isRunning = false; private set }

    override fun onServiceConnected() { super.onServiceConnected(); isRunning = true }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == applicationContext.packageName || pkg == "com.android.systemui") return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> orchestrator.onAppChanged(pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                try {
                    val root = rootInActiveWindow ?: return
                    val text = StringBuilder()
                    extractText(root, text, 5, 0)
                    root.recycle()
                    val t = text.toString()
                    if (t.isNotBlank() && t.length > 3) orchestrator.onScreenTextDetected(t, pkg)
                } catch (_: Exception) {}
            }
        }
    }

    private fun extractText(node: AccessibilityNodeInfo, sb: StringBuilder, max: Int, depth: Int) {
        if (depth > max) return
        val vid = node.viewIdResourceName
        if (vid != null && (vid.contains("url") || vid.contains("address") || vid.contains("search")))
            node.text?.let { sb.append(it).append(" ") }
        node.text?.let { if (it.length in 4..500) sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            try { val c = node.getChild(i) ?: continue; extractText(c, sb, max, depth + 1); c.recycle() }
            catch (_: Exception) {}
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { super.onDestroy(); isRunning = false }
}
