package com.guardianshield.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.data.model.ActivityLog
import com.guardianshield.app.detector.ScrollAddictionDetector
import com.guardianshield.app.manager.AiContentDetector
import com.guardianshield.app.manager.TempBlockManager
import com.guardianshield.app.ui.overlay.BlockOverlayActivity
import com.guardianshield.app.ui.scroll.ScrollSuggestionActivity
import com.guardianshield.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Core engine:
 *  - Detects current foreground app
 *  - Blocks (overlay) if package is blocklisted or AI-blocked or schedule-blocked
 *  - Detects scroll addiction on short-video apps → shows Quran suggestion
 *  - Scans on-screen text for AI flagged content → strikes
 *  - Intercepts uninstall attempts from Play Store / Settings
 */
class GuardianAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scrollDetector = ScrollAddictionDetector()
    private var lastForegroundPkg: String? = null

    private val repo by lazy { GuardianApp.get().repository }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Guardian AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        // Skip self.
        if (pkg == packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowChange(pkg, event)
            AccessibilityEvent.TYPE_VIEW_SCROLLED        -> handleScroll(pkg, event)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED    -> handleContentChange(pkg, event)
            AccessibilityEvent.TYPE_VIEW_CLICKED         -> handleClick(pkg, event)
            else -> { /* ignore */ }
        }
    }

    // ---------------------------------------------------------------------
    // 1) Foreground app gating (blocklist + AI block + schedule)
    // ---------------------------------------------------------------------
    private fun handleWindowChange(pkg: String, event: AccessibilityEvent) {
        if (pkg == lastForegroundPkg) return
        lastForegroundPkg = pkg

        // Intercept Play Store / Settings uninstall flow for self.
        if (pkg == "com.android.vending" || pkg == "com.android.settings") {
            scanForUninstallAttempt(event)
        }

        scope.launch {
            val rule = repo.getRule(pkg)
            // Whitelist short-circuits all blocking.
            if (rule?.isWhitelisted == true) return@launch

            // 1) AI temp block?
            TempBlockManager.isTempBlocked(pkg)?.let { tb ->
                showBlock(pkg, Constants.REASON_AI, tb.until - System.currentTimeMillis(), tb.strikeCount)
                return@launch
            }

            // 2) Blocklist?
            if (rule?.isBlocked == true) {
                showBlock(pkg, Constants.REASON_BLOCKLIST, 0L, 0)
                return@launch
            }

            // 3) Schedule block?
            if (ScheduleEvaluator.isInBlockingWindow(repo, pkg)) {
                showBlock(pkg, Constants.REASON_SCHEDULE, 0L, 0)
                return@launch
            }
        }
    }

    private fun showBlock(pkg: String, reason: String, durationLeft: Long, strikes: Int) {
        val i = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(Constants.EXTRA_BLOCK_PACKAGE, pkg)
            putExtra(Constants.EXTRA_BLOCK_REASON, reason)
            putExtra(Constants.EXTRA_BLOCK_DURATION, durationLeft)
            putExtra(Constants.EXTRA_STRIKE_COUNT, strikes)
        }
        startActivity(i)
        scope.launch {
            repo.log(ActivityLog(
                packageName = pkg,
                eventType = if (reason == Constants.REASON_AI) "AI_BLOCK_24H" else "BLOCK",
                details = "reason=$reason",
                strikeCount = strikes
            ))
        }
    }

    // ---------------------------------------------------------------------
    // 2) Scroll addiction detection
    // ---------------------------------------------------------------------
    private fun handleScroll(pkg: String, event: AccessibilityEvent) {
        if (pkg !in Constants.SHORT_VIDEO_PACKAGES) return
        // Only count upward swipes (scroll Y up = positive delta).
        val isUpward = event.scrollDeltaY < 0 || event.scrollY > 0
        if (!isUpward) return

        if (scrollDetector.recordScroll(pkg)) {
            val i = Intent(this, ScrollSuggestionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Constants.EXTRA_BLOCK_PACKAGE, pkg)
            }
            startActivity(i)
            scope.launch {
                repo.log(ActivityLog(
                    packageName = pkg,
                    eventType = "SCROLL_REMINDER",
                    details = "threshold reached"
                ))
            }
        }
    }

    // ---------------------------------------------------------------------
    // 3) AI text scanning (strike pipeline)
    // ---------------------------------------------------------------------
    private fun handleContentChange(pkg: String, event: AccessibilityEvent) {
        val rule = runCatching { GuardianApp.get().database }.getOrNull() ?: return
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        val text = collectVisibleText(root, max = 600)
        if (text.isBlank()) return

        if (AiContentDetector.isStrike(text)) {
            val count = TempBlockManager.recordAiDetection(pkg)
            if (count == -1) return // already blocked

            scope.launch {
                repo.log(ActivityLog(
                    packageName = pkg,
                    eventType = if (count >= Constants.STRIKE_THRESHOLD) "AI_BLOCK_24H" else "AI_WARN",
                    details = "AI flagged content",
                    strikeCount = count
                ))
            }

            if (count >= Constants.STRIKE_THRESHOLD) {
                showBlock(pkg, Constants.REASON_AI, Constants.AI_BLOCK_DURATION_MS, count)
            } else {
                // Warning overlay (not full 24h)
                val i = Intent(this, BlockOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Constants.EXTRA_BLOCK_PACKAGE, pkg)
                    putExtra(Constants.EXTRA_BLOCK_REASON, "ai_warn")
                    putExtra(Constants.EXTRA_STRIKE_COUNT, count)
                }
                startActivity(i)
            }
        }
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo, max: Int): String {
        val sb = StringBuilder()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(node)
        while (stack.isNotEmpty() && sb.length < max) {
            val n = stack.removeLast()
            n.text?.let { sb.append(it).append(' ') }
            n.contentDescription?.let { sb.append(it).append(' ') }
            for (i in 0 until n.childCount) n.getChild(i)?.let(stack::addLast)
        }
        return sb.toString().take(max)
    }

    // ---------------------------------------------------------------------
    // 4) Uninstall protection (block the Uninstall button)
    // ---------------------------------------------------------------------
    private fun handleClick(pkg: String, event: AccessibilityEvent) {
        val src = event.source ?: return
        val txt = (src.text ?: "").toString().lowercase()
        if (("uninstall" in txt || "আনইনস্টল" in txt) && targetIsSelf(src)) {
            // Re-route home and trigger tamper alert
            performGlobalAction(GLOBAL_ACTION_HOME)
            startActivity(Intent(this, com.guardianshield.app.ui.admin.TamperAlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun targetIsSelf(node: AccessibilityNodeInfo): Boolean {
        // Heuristic: look upwards for our app name string on the same screen.
        val root = rootInActiveWindow ?: return false
        val text = collectVisibleText(root, 400).lowercase()
        return "guardian shield" in text || packageName in text
    }

    private fun scanForUninstallAttempt(event: AccessibilityEvent) {
        val root = rootInActiveWindow ?: return
        val txt = collectVisibleText(root, 400).lowercase()
        if (("uninstall" in txt || "remove" in txt) &&
            ("guardian shield" in txt || packageName in txt)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            startActivity(Intent(this, com.guardianshield.app.ui.admin.TamperAlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onInterrupt() { /* no-op */ }

    companion object { private const val TAG = "GuardianA11y" }
}
