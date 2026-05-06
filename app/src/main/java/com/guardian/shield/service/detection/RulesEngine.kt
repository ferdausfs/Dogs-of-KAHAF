package com.guardian.shield.service.detection

import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RulesEngine @Inject constructor() {

    companion object {
        const val OUR_PACKAGE = "com.guardian.shield"
        private const val TAG = "Guardian_Rules"

        private val ESSENTIAL_SYSTEM = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.sec.android.inputmethod",
            "com.touchtype.swiftkey",
            "com.swiftkey.swiftkeyapp",
            "com.miui.home",
            "com.sec.android.app.launcher",
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
        // CRITICAL FIX: Added word-boundary guards (?<!\w) and (?!\w)
        // Without this, keyword "sex" would also block "Sussex", "Essex" etc.
        // Now only exact word matches trigger a block.
        keywordPattern = if (keywords.isEmpty()) null
        else Regex(
            keywords.joinToString("|") { "(?i)(?<![\\w])${Regex.escape(it.lowercase().trim())}(?![\\w])" }
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

    fun isEssentialSystem(pkg: String): Boolean {
        if (pkg in ESSENTIAL_SYSTEM) return true
        ESSENTIAL_PREFIXES.forEach { if (pkg.startsWith(it)) return true }
        return false
    }

    fun isSystemPackage(pkg: String): Boolean = isEssentialSystem(pkg)

    fun isProtectionActive(): Boolean = isProtectionEnabled

    fun OUR_PACKAGE_HOLDER(): String = OUR_PACKAGE
}