package com.kahaf.guardian.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.kahaf.guardian.engine.detection.DetectionOrchestrator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KahafAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var orchestrator: DetectionOrchestrator

    companion object {
        @Volatile
        var isRunning = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                handleContentChanged(event)
            }
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // Skip our own package
        if (packageName == applicationContext.packageName) return
        if (packageName == "com.android.systemui") return

        // Trigger app detection
        orchestrator.onAppChanged(packageName)
    }

    private fun handleContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // Skip system UI
        if (packageName == "com.android.systemui") return
        if (packageName == applicationContext.packageName) return

        // Extract text from screen for keyword detection
        try {
            val rootNode = rootInActiveWindow ?: return
            val screenText = extractText(rootNode)
            if (screenText.isNotBlank() && screenText.length > 3) {
                orchestrator.onScreenTextDetected(screenText, packageName)
            }
            rootNode.recycle()
        } catch (e: Exception) {
            // Silently handle - accessibility node may become stale
        }
    }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val builder = StringBuilder()
        extractTextRecursive(node, builder, maxDepth = 5, currentDepth = 0)
        return builder.toString()
    }

    private fun extractTextRecursive(
        node: AccessibilityNodeInfo,
        builder: StringBuilder,
        maxDepth: Int,
        currentDepth: Int
    ) {
        if (currentDepth > maxDepth) return

        // Check for URL bar content specifically
        val viewId = node.viewIdResourceName
        if (viewId != null && (viewId.contains("url") || viewId.contains("address") || viewId.contains("search"))) {
            node.text?.let { text ->
                builder.append(text).append(" ")
            }
        }

        // Also collect general text
        node.text?.let { text ->
            if (text.length in 4..500) {
                builder.append(text).append(" ")
            }
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            try {
                val child = node.getChild(i) ?: continue
                extractTextRecursive(child, builder, maxDepth, currentDepth + 1)
                child.recycle()
            } catch (e: Exception) {
                // Node may be recycled
            }
        }
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}