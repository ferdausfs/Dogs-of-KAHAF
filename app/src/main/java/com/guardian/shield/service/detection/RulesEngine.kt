package com.guardian.shield.service.detection

import android.content.Context
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.domain.usecase.GetAllAppRulesSyncUseCase
import com.guardian.shield.domain.usecase.GetAllKeywordsSyncUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX-LOG (vs original):
 *  - BUG #8: pkg.startsWith("com.android.systemui") matched "com.android.systemuixyz".
 *            Now exact-match against a Set, plus a separate set of launcher prefixes
 *            that are intentionally prefix-matched (because every OEM uses a different
 *            launcher package).
 *  - BUG #11: previously, when an app was BOTH blocked and whitelisted, it was
 *            silently allowed because the reload step filtered out blocked rules
 *            that were also whitelisted. That is the documented behaviour
 *            (whitelist > blocklist) but the data model allowed both flags to be
 *            true simultaneously, which caused weird UX. Behaviour is preserved
 *            (whitelist wins) but is now explicit and documented.
 */
@Singleton
class RulesEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getApps: GetAllAppRulesSyncUseCase,
    private val getKws: GetAllKeywordsSyncUseCase
) {
    companion object {
        const val ACTION_RULES_CHANGED = "com.guardian.shield.ACTION_RULES_CHANGED"

        // Exact-match system / IME packages (BUG #8 fix).
        private val SYSTEM_EXACT = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.google.android.inputmethod.latin",
            "com.google.android.gms",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.samsung.android.honeyboard",
            "android"
        )
        // Launcher / IME prefix matches (kept intentionally because launcher packages
        // legitimately vary across OEMs: com.android.launcher3, com.miui.home,
        // com.sec.android.app.launcher, etc.).
        private val LAUNCHER_PREFIXES = listOf(
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.realme.launcher",
            "com.vivo.launcher"
        )
    }

    private val mutex = Mutex()

    @Volatile private var blockedSet: Set<String> = emptySet()
    @Volatile private var whitelistSet: Set<String> = emptySet()
    @Volatile private var keywords: List<Pair<String, Boolean>> = emptyList()

    suspend fun reload() = mutex.withLock {
        val apps = getApps()
        // Whitelist wins → if both flags are true, treat as whitelisted only.
        whitelistSet = apps.filter { it.isWhitelisted }.map { it.packageName }.toSet()
        blockedSet  = apps.filter { it.isBlocked && !it.isWhitelisted }
            .map { it.packageName }.toSet()
        keywords    = getKws().map { it.keyword.lowercase() to it.isRegex }
    }

    private fun isSystemPackage(pkg: String): Boolean {
        if (pkg in SYSTEM_EXACT) return true
        return LAUNCHER_PREFIXES.any { pkg.startsWith(it) }
    }

    /** Whitelist → system UI → own pkg → blocked list → allow */
    fun evaluatePackage(pkg: String): DetectionResult {
        if (pkg == context.packageName) return DetectionResult.Allow
        if (isSystemPackage(pkg)) return DetectionResult.Allow
        if (whitelistSet.contains(pkg)) return DetectionResult.Allow
        if (blockedSet.contains(pkg)) return DetectionResult.Block(BlockReason.APP_BLOCKED, pkg)
        return DetectionResult.Allow
    }

    fun evaluateText(text: CharSequence?): DetectionResult {
        if (text.isNullOrBlank() || keywords.isEmpty()) return DetectionResult.Allow
        val lower = text.toString().lowercase()
        for ((kw, isRegex) in keywords) {
            val match = if (isRegex) runCatching { Regex(kw).containsMatchIn(lower) }.getOrDefault(false)
                        else lower.contains(kw)
            if (match) return DetectionResult.Block(BlockReason.KEYWORD_MATCH, kw)
        }
        return DetectionResult.Allow
    }

    fun isWhitelisted(pkg: String): Boolean = whitelistSet.contains(pkg)

    fun canBlock(pkg: String): Boolean {
        if (pkg == context.packageName) return false
        if (isSystemPackage(pkg)) return false
        if (whitelistSet.contains(pkg)) return false
        return true
    }
}
