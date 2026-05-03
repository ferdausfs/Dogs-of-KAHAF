package com.guardian.shield.service.detection

import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RulesEngine — single authority for ALL block decisions.
 *
 * Priority order (immutable):
 *   1. OWN package     → always allow
 *   2. WHITELIST       → always allow
 *   3. BLOCKED apps    → block
 *   4. KEYWORD match   → block
 *   5. AI detection    → block
 *   6. Default         → allow
 */
@Singleton
class RulesEngine @Inject constructor() {

    companion object {
        const val OUR_PACKAGE = "com.guardian.shield"
        private const val TAG = "Guardian_Rules"

        private val SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.sec.android.inputmethod",
            "com.touchtype.swiftkey",
            "com.android.settings.intelligence",
            "com.miui.msa.global"
        )

        private val SYSTEM_PREFIXES = arrayOf(
            "com.android.systemui.",
            "com.oneplus.",
            "com.nothing.launcher",
            "com.samsung.android.app.taskbar"
        )
    }

    @Volatile private var blockedPackages: Set<String>     = emptySet()
    @Volatile private var whitelistedPackages: Set<String> = emptySet()
    @Volatile private var activeKeywords: List<String>     = emptyList()
    @Volatile private var isKeywordDetectionOn: Boolean    = true
    @Volatile private var isProtectionEnabled: Boolean     = true
    @Volatile private var isStrictMode: Boolean            = false

    // FIX: Pre-compiled Regex for keyword matching — O(n) instead of O(n×m)
    @Volatile private var keywordPattern: Regex? = null

    // ── Cache refresh ──────────────────────────────────────────────────

    fun refreshBlockedApps(packages: Set<String>) {
        blockedPackages = packages
        Timber.d("$TAG blocked list refreshed: ${packages.size} apps")
    }

    fun refreshWhitelistedApps(packages: Set<String>) {
        whitelistedPackages = packages
        Timber.d("$TAG whitelist refreshed: ${packages.size} apps")
    }

    fun refreshKeywords(keywords: List<String>) {
        activeKeywords = keywords.map { it.lowercase().trim() }
        // FIX: Build combined Regex pattern — single pass instead of per-keyword contains()
        keywordPattern = if (keywords.isEmpty()) null
        else Regex(
            keywords.joinToString("|") { Regex.escape(it.lowercase().trim()) }
        )
        Timber.d("$TAG keywords refreshed: ${keywords.size} keywords")
    }

    fun setKeywordDetectionEnabled(enabled: Boolean) { isKeywordDetectionOn = enabled }
    fun setProtectionEnabled(enabled: Boolean)        { isProtectionEnabled  = enabled }
    fun setStrictMode(enabled: Boolean)               { isStrictMode         = enabled }

    // ── Main evaluation ────────────────────────────────────────────────

    fun evaluateApp(packageName: String): DetectionResult {
        if (!isProtectionEnabled) return DetectionResult.Allow

        // Rule 1: Own package
        if (packageName == OUR_PACKAGE) return DetectionResult.Allow

        // Rule 2: System UI
        if (isSystemPackage(packageName)) return DetectionResult.Allow

        // Rule 3: Whitelist
        if (packageName in whitelistedPackages) {
            Timber.d("$TAG ALLOW (whitelist): $packageName")
            return DetectionResult.Whitelist
        }

        // Rule 4: Blocked app list
        if (packageName in blockedPackages) {
            Timber.d("$TAG BLOCK (app list): $packageName")
            return DetectionResult.Block(BlockReason.APP_BLOCKED, packageName)
        }

        return DetectionResult.Allow
    }

    fun evaluateText(packageName: String, text: String): DetectionResult {
        if (!isProtectionEnabled)    return DetectionResult.Allow
        if (!isKeywordDetectionOn)   return DetectionResult.Allow
        if (packageName == OUR_PACKAGE) return DetectionResult.Allow
        if (packageName in whitelistedPackages) return DetectionResult.Whitelist

        val lower = text.lowercase()

        // FIX: Single-pass Regex match instead of O(n×m) contains() loop
        val hit = keywordPattern?.find(lower)?.value

        return if (hit != null) {
            Timber.d("$TAG BLOCK (keyword '$hit'): $packageName")
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
        // FIX: Consistent priority order — own package and whitelist first
        if (packageName == OUR_PACKAGE) return DetectionResult.Allow
        if (packageName in whitelistedPackages) return DetectionResult.Whitelist
        if (!isProtectionEnabled) return DetectionResult.Allow

        return if (unsafeScore >= threshold) {
            Timber.d("$TAG BLOCK (AI score=$unsafeScore): $packageName")
            DetectionResult.Block(
                BlockReason.AI_DETECTED,
                "${(unsafeScore * 100).toInt()}% unsafe"
            )
        } else {
            DetectionResult.Allow
        }
    }

    fun isWhitelisted(packageName: String): Boolean =
        packageName == OUR_PACKAGE || packageName in whitelistedPackages

    // FIX: isSystemUi() was redundant alias — replaced with isSystemPackage()
    fun isSystemPackage(pkg: String): Boolean {
        if (pkg in SYSTEM_PACKAGES) return true
        SYSTEM_PREFIXES.forEach { if (pkg.startsWith(it)) return true }
        return false
    }

    fun isProtectionActive(): Boolean = isProtectionEnabled
}