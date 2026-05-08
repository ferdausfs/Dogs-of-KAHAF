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

/**
 * v8 FIX-LOG (stability pass):
 *  • BUG-08 → all read paths now go through a single immutable [RulesSnapshot]
 *    that is replaced atomically (single @Volatile reference write) at the
 *    end of reload(). Previously, blockedSet / whitelistSet / keywords were
 *    three separate @Volatile fields updated sequentially — a concurrent
 *    evaluator could observe a half-updated state (new blocked set with old
 *    whitelist, etc.). With a single snapshot reference, every evaluator
 *    sees a fully-coherent view.
 */
@Singleton
class RulesEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getApps: GetAllAppRulesSyncUseCase,
    private val getKws: GetAllKeywordsSyncUseCase
) {
    companion object {
        const val ACTION_RULES_CHANGED = "com.guardian.shield.ACTION_RULES_CHANGED"
    }

    /** Atomic, fully-coherent view of all rules. Replaced as one reference. */
    private data class RulesSnapshot(
        val blocked: Set<String>,
        val whitelist: Set<String>,
        val keywords: List<Pair<String, Boolean>>,
        val inputMethods: Set<String>
    )

    private val mutex = Mutex()
    private val ownPackage = context.packageName

    @Volatile private var snapshot: RulesSnapshot = RulesSnapshot(
        blocked = emptySet(),
        whitelist = emptySet(),
        keywords = emptyList(),
        inputMethods = AppClassifier.loadInputMethodPackages(context)
    )

    suspend fun reload() = mutex.withLock {
        val apps = getApps()
        val kws = getKws()
        val ime = AppClassifier.loadInputMethodPackages(context)

        // Build the new snapshot completely BEFORE swapping the volatile
        // reference — readers either see the fully-old snapshot or the
        // fully-new one, never a mix.
        val newSnapshot = RulesSnapshot(
            whitelist = apps.filter { it.isWhitelisted }.map { it.packageName }.toSet(),
            blocked = apps.filter { it.isBlocked && !it.isWhitelisted }
                .map { it.packageName }.toSet(),
            keywords = kws.map { it.keyword.lowercase() to it.isRegex },
            inputMethods = ime
        )
        snapshot = newSnapshot
    }

    private fun isAlwaysAllowed(pkg: String, snap: RulesSnapshot): Boolean =
        AppClassifier.isAlwaysAllowedPackage(ownPackage, pkg, snap.inputMethods)

    fun evaluatePackage(pkg: String): DetectionResult {
        val snap = snapshot
        if (isAlwaysAllowed(pkg, snap)) return DetectionResult.Allow
        if (snap.whitelist.contains(pkg)) return DetectionResult.Allow
        if (snap.blocked.contains(pkg)) return DetectionResult.Block(BlockReason.APP_BLOCKED, pkg)
        return DetectionResult.Allow
    }

    fun evaluateText(text: CharSequence?): DetectionResult {
        val snap = snapshot
        if (text.isNullOrBlank() || snap.keywords.isEmpty()) return DetectionResult.Allow
        val lower = text.toString().lowercase()
        for ((kw, isRegex) in snap.keywords) {
            val match = if (isRegex) {
                runCatching { Regex(kw).containsMatchIn(lower) }.getOrDefault(false)
            } else {
                lower.contains(kw)
            }
            if (match) return DetectionResult.Block(BlockReason.KEYWORD_MATCH, kw)
        }
        return DetectionResult.Allow
    }

    fun isWhitelisted(pkg: String): Boolean = snapshot.whitelist.contains(pkg)

    fun canBlock(pkg: String): Boolean {
        val snap = snapshot
        if (isAlwaysAllowed(pkg, snap)) return false
        if (snap.whitelist.contains(pkg)) return false
        return true
    }
}
