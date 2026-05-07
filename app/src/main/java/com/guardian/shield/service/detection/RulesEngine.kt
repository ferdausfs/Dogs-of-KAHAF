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

@Singleton
class RulesEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getApps: GetAllAppRulesSyncUseCase,
    private val getKws: GetAllKeywordsSyncUseCase
) {
    companion object {
        // Broadcast this action whenever rules/keywords change so the service reloads its cache
        const val ACTION_RULES_CHANGED = "com.guardian.shield.ACTION_RULES_CHANGED"
    }

    private val mutex = Mutex()
    private val systemUiPackages = setOf(
        "com.android.systemui", "com.google.android.inputmethod.latin",
        "com.android.launcher", "com.google.android.apps.nexuslauncher",
        "com.android.settings"
    )

    @Volatile private var blockedSet: Set<String> = emptySet()
    @Volatile private var whitelistSet: Set<String> = emptySet()
    @Volatile private var keywords: List<Pair<String, Boolean>> = emptyList()

    suspend fun reload() = mutex.withLock {
        val apps = getApps()
        blockedSet  = apps.filter { it.isBlocked && !it.isWhitelisted }.map { it.packageName }.toSet()
        whitelistSet = apps.filter { it.isWhitelisted }.map { it.packageName }.toSet()
        keywords    = getKws().map { it.keyword to it.isRegex }
    }

    /** Whitelist → system UI → own pkg → blocked list → allow */
    fun evaluatePackage(pkg: String): DetectionResult {
        if (pkg == context.packageName) return DetectionResult.Allow
        if (systemUiPackages.any { pkg.startsWith(it) }) return DetectionResult.Allow
        if (whitelistSet.contains(pkg)) return DetectionResult.Allow        // ← allowlist wins
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

    /**
     * Returns true if it is safe to run any blocking check (AI or keyword) on this package.
     * False for: our own app, system UI, or explicitly whitelisted packages.
     */
    fun canBlock(pkg: String): Boolean {
        if (pkg == context.packageName) return false
        if (systemUiPackages.any { pkg.startsWith(it) }) return false
        if (whitelistSet.contains(pkg)) return false
        return true
    }
