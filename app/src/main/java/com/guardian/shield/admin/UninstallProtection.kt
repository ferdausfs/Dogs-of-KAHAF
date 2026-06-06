package com.guardian.shield.admin

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

/**
 * Phase 5 — Uninstall protection helpers.
 *
 * The Accessibility Service watches `com.android.settings` and similar
 * package-manager UIs. When the Guardian Shield app-info page is visible AND
 * an "Uninstall" / "Force Stop" / "Disable" button is present, this helper
 * bounces the user back to the Home screen.
 *
 * The actual node search is intentionally tolerant — different OEM skins
 * (Samsung, Xiaomi, Realme…) label the buttons differently.
 */
object UninstallProtection {

    /**
     * Top-level packages we consider "package management" surfaces.
     * Anything outside this set is ignored by [isManagingOurApp].
     */
    private val PACKAGE_MANAGER_PKGS: Set<String> = setOf(
        "com.android.settings",
        "com.miui.securitycenter",
        "com.samsung.android.lool",
        "com.samsung.android.app.appsedge",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.oneplus.security",
        "com.oppo.safe",
        "com.iqoo.secure",
        "com.android.vending", // Play Store for uninstallation
        "com.google.android.settings.intelligence",
        "com.android.systemui"
    )

    /**
     * Text fragments that, when found together with our own package label,
     * indicate the user is about to remove or hobble Guardian Shield.
     */
    private val DANGEROUS_TEXTS: List<String> = listOf(
        "Uninstall", "uninstall",
        "Uninstall updates", "Uninstall update",
        "Disable", "disable",
        "Force stop", "Force Stop", "force stop",
        "Clear data", "Clear storage",
        "Manage space", "Clear all data",
        "আনইনস্টল", "নিষ্ক্রিয়", "জোর করে বন্ধ", "সব ডাটা মুছুন",
        "卸载", "停用", "Desinstalar", "Forzar detención"
    )

    /**
     * Text fragments related to Device Admin or Accessibility tampering.
     */
    private val TAMPER_SYSTEM_TEXTS: List<String> = listOf(
        "Device admin apps", "Device admin", "অ্যাডমিন অ্যাপ",
        "Accessibility", "এক্সেসিবিলিটি",
        "Downloaded apps", "Installed services", "Accessibility services",
        "Manage accessibility", "সংযুক্ত পরিষেবা"
    )

    /**
     * Identifiers we treat as "this is our app's app-info page".
     */
    private val OUR_LABELS: List<String> = listOf(
        "Guardian Shield",
        "guardian shield",
        "com.guardian.shield"
    )

    fun isPackageManager(pkg: String): Boolean = PACKAGE_MANAGER_PKGS.any {
        pkg == it || pkg.startsWith("$it.")
    }

    /**
     * True if we can confirm that:
     *   1) we're in a package-manager-style screen, AND
     *   2) some node references our app, AND
     *   3) at least one dangerous action button or system setting is visible.
     */
    fun isManagingOurApp(service: AccessibilityService): Boolean {
        val root = try { service.rootInActiveWindow } catch (_: Throwable) { null } ?: return false
        return try {
            val text = collectText(root)
            val mentionsOurs = OUR_LABELS.any { text.contains(it, ignoreCase = true) }
            if (!mentionsOurs) return false

            val isDangerousAction = DANGEROUS_TEXTS.any { text.contains(it) }
            val isSystemTamper = TAMPER_SYSTEM_TEXTS.any { text.contains(it) }

            isDangerousAction || isSystemTamper
        } catch (t: Throwable) {
            Timber.w(t, "isManagingOurApp failed")
            false
        }
    }

    /**
     * Best-effort BFS that collects every visible text/contentDesc up to a
     * shallow depth. We intentionally keep it bounded so this stays O(1)-ish.
     */
    private fun collectText(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 150) {
            val n = queue.removeFirst()
            visited++
            n.text?.let { sb.append(it).append(' ') }
            n.contentDescription?.let { sb.append(it).append(' ') }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return sb.toString()
    }
}
