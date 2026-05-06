package com.guardian.shield.service.detection

import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RulesEngine — single authority for ALL block decisions.
 *
 * Priority:
 *   1. OWN package        → always allow
 *   2. ESSENTIAL system   → always allow (launcher, systemui only)
 *   3. WHITELIST          → always allow
 *   4. BLOCKED apps       → block (INCLUDES Settings, DNS, etc. if user added them)
 *   5. KEYWORD match      → block
 *   6. AI detection       → block
 *   7. Default            → allow
 */
@Singleton
class RulesEngine @Inject constructor() {

    companion object {
        const val OUR_PACKAGE = "com.guardian.shield"
        private const val TAG = "Guardian_Rules"

        // FIX: Only ESSENTIAL system UI - everything else can be blocked
        // Settings is NOT here so user can block it for tamper protection
        private val ESSENTIAL_SYSTEM = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.sec.android.inputmethod",
            "com.touchtype.swiftkey",
            "com.swiftkey.swiftkeyapp",
            "com.miui.home",        // Xiaomi launcher
            "com.sec.android.app.launcher", // Samsung launcher
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.oneplus.launcher",
            "net.oneplus.launcher"
        )

        private val ESSENTIAL_PREFIXES = arrayOf(
            "com.android.systemui."
        )
    }

    @Volatile private var blockedPackages: Set<String> = emptySet()
    @Volatile private var whitelistedPackages: Set<String> = emptySet()
    @Volatile private var activeKeywords: List<String> = emptyList()
    @Volatile private var isKeywordDetectionOn: Boolean = true
    @Volatile private var isProtectionEnabled: Boolean = true
    @Volatile private var isStrictMode: Boolean = false

    @Volatile private var keywordPattern: Regex? = null

    fun refreshBlockedApps(packages: Set<String>) {
        blockedPackages = packages
        Timber.d("$TAG blocked list refreshed: ${packages.size} apps -> $packages")
    }

    fun refreshWhitelistedApps(packages: Set<String>) {
        whitelistedPackages = packages
        Timber.d("$TAG whitelist refreshed: ${packages.size} apps")
    }

    fun refreshKeywords(keywords: List<String>) {
        activeKeywords = keywords.map { it.lowercase().trim() }
        keywordPattern = if (keywords.isEmpty()) null
        else Regex(
            keywords.joinToString("|") { Regex.escape(it.lowercase().trim()) }
        )
        Timber.d("$TAG keywords refreshed: ${keywords.size} keywords")
    }

    fun setKeywordDetectionEnabled(enabled: Boolean) { isKeywordDetectionOn = enabled }
    fun setProtectionEnabled(enabled: Boolean) { isProtectionEnabled = enabled }
    fun setStrictMode(enabled: Boolean) { isStrictMode = enabled }

    fun evaluateApp(packageName: String): DetectionResult {
        if (!isProtectionEnabled) return DetectionResult.Allow

        if (packageName == OUR_PACKAGE) return DetectionResult.Allow
        if (isEssentialSystem(packageName)) return DetectionResult.Allow

        if (packageName in whitelistedPackages) {
            return DetectionResult.Whitelist
        }

        // FIX: Blocked list overrides everything except essential system
        // This means Settings, DNS apps, etc. CAN be blocked if user adds them
        if (packageName in blockedPackages) {
            Timber.w("$TAG BLOCK (app list): $packageName")
            return DetectionResult.Block(BlockReason.APP_BLOCKED, packageName)
        }

        return DetectionResult.Allow
    }

    fun evaluateText(packageName: String, text: String): DetectionResult {
        if (!isProtectionEnabled) return DetectionResult.Allow
        if (!isKeywordDetectionOn) return DetectionResult.Allow
        if (packageName == OUR_PACKAGE) return DetectionResult.Allow
        if (packageName in whitelistedPackages) return DetectionResult.Whitelist

        val lower = text.lowercase()
        val hit = keywordPattern?.find(lower)?.value

        return if (hit != null) {
            Timber.w("$TAG BLOCK (keyword '$hit'): $packageName")
            DetectionResult.Block(BlockReason.KEYWORD_DETECTED, hit)
        } else {
            DetectionResult.Allow
        }
    }

    fun evaluateAiResult(
        packageName: String,
        unsafeScore: Float,
        threshold: Float
    ): DetectionResult {
        if (packageName == OUR_PACKAGE) return DetectionResult.Allow
        if (packageName in whitelistedPackages) return DetectionResult.Whitelist
        if (!isProtectionEnabled) return DetectionResult.Allow

        return if (unsafeScore >= threshold) {
            Timber.w("$TAG BLOCK (AI score=$unsafeScore >= $threshold): $packageName")
            DetectionResult.Block(
                BlockReason.AI_DETECTED,
                "${(unsafeScore * 100).toInt()}% unsafe"
            )
        } else {
            Timber.d("$TAG AI safe: $packageName score=$unsafeScore")
            DetectionResult.Allow
        }
    }

    fun isWhitelisted(packageName: String): Boolean =
        packageName == OUR_PACKAGE || packageName in whitelistedPackages

    // FIX: Renamed - only ESSENTIAL apps that can never be blocked (launcher, keyboard, systemui)
    fun isEssentialSystem(pkg: String): Boolean {
        if (pkg in ESSENTIAL_SYSTEM) return true
        ESSENTIAL_PREFIXES.forEach { if (pkg.startsWith(it)) return true }
        return false
    }
      
    // Kept for backward compat with service code
    fun isSystemPackage(pkg: String): Boolean = isEssentialSystem(pkg)

    fun isProtectionActive(): Boolean = isProtectionEnabled
    // Helper for service code that needs OUR_PACKAGE constant
fun OUR_PACKAGE_HOLDER(): String = OUR_PACKAGE
}
