package com.guardian.shield.service.detection

import android.content.Context
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.domain.usecase.GetAllAppRulesSyncUseCase
import com.guardian.shield.domain.usecase.GetAllKeywordsSyncUseCase
import com.guardian.shield.util.AppClassifier
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
        const val ACTION_RULES_CHANGED = "com.guardian.shield.ACTION_RULES_CHANGED"
    }

    private val mutex = Mutex()
    private val ownPackage = context.packageName

    @Volatile private var blockedSet: Set<String> = emptySet()
    @Volatile private var whitelistSet: Set<String> = emptySet()
    @Volatile private var keywords: List<Pair<String, Boolean>> = emptyList()
    @Volatile private var inputMethodPackages: Set<String> = AppClassifier.loadInputMethodPackages(context)

    suspend fun reload() = mutex.withLock {
        val apps = getApps()
        inputMethodPackages = AppClassifier.loadInputMethodPackages(context)
        whitelistSet = apps.filter { it.isWhitelisted }.map { it.packageName }.toSet()
        blockedSet = apps.filter { it.isBlocked && !it.isWhitelisted }.map { it.packageName }.toSet()
        keywords = getKws().map { it.keyword.lowercase() to it.isRegex }
    }

    private fun isAlwaysAllowed(pkg: String): Boolean =
        AppClassifier.isAlwaysAllowedPackage(ownPackage, pkg, inputMethodPackages)

    fun evaluatePackage(pkg: String): DetectionResult {
        if (isAlwaysAllowed(pkg)) return DetectionResult.Allow
        if (whitelistSet.contains(pkg)) return DetectionResult.Allow
        if (blockedSet.contains(pkg)) return DetectionResult.Block(BlockReason.APP_BLOCKED, pkg)
        return DetectionResult.Allow
    }

    fun evaluateText(text: CharSequence?): DetectionResult {
        if (text.isNullOrBlank() || keywords.isEmpty()) return DetectionResult.Allow
        val lower = text.toString().lowercase()
        for ((kw, isRegex) in keywords) {
            val match = if (isRegex) {
                runCatching { Regex(kw).containsMatchIn(lower) }.getOrDefault(false)
            } else {
                lower.contains(kw)
            }
            if (match) return DetectionResult.Block(BlockReason.KEYWORD_MATCH, kw)
        }
        return DetectionResult.Allow
    }

    fun isWhitelisted(pkg: String): Boolean = whitelistSet.contains(pkg)

    fun canBlock(pkg: String): Boolean {
        if (isAlwaysAllowed(pkg)) return false
        if (whitelistSet.contains(pkg)) return false
        return true
    }
}
