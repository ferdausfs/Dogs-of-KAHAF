package com.guardian.shield.service.detection

/**
 * R14 (v3.8.3) — Commitment Tamper-Shield package set.
 *
 * During an active Time-Lock (or its short cooldown) these system surfaces
 * are UNREACHABLE, wherever they appear — full screen, split-screen pane or
 * pop-up window:
 *  - com.android.settings            → App info (clear data), Device admin
 *  - com.android.vending             → Play Store uninstall
 *  - *packageinstaller*              → direct APK uninstall/replace flows
 *  - com.miui.securitycenter         → MIUI's own uninstall/data manager
 *
 * The page-level [com.guardian.shield.admin.UninstallProtection] heuristic
 * only guarded screens already TARGETING Guardian; OEM variants and
 * split-screen layouts slipped through. This is the umbrella above it.
 */
object LockShield {
    val PACKAGES: Set<String> = setOf(
        "com.android.settings",
        "com.android.vending",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.securitycenter"
    )

    fun isTarget(pkg: String): Boolean = PACKAGES.contains(pkg)

    /** Block-overlay detail token (matched by BlockOverlayActivity copy). */
    const val DETAIL = "lockshield_active"
}
