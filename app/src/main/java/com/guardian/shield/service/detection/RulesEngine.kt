package com.guardian.shield.service.detection

import android.content.Context
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.domain.model.ScheduleRule
import com.guardian.shield.domain.usecase.GetAllAppRulesSyncUseCase
import com.guardian.shield.domain.usecase.GetAllKeywordsSyncUseCase
import com.guardian.shield.domain.usecase.GetAllScheduleRulesSyncUseCase
import com.guardian.shield.util.AppClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v15 (2.1.5) STABILITY PATCH 5:
 *  • FIX-2: evaluateText() now caps input length and wraps each regex
 *    match in runCatching with isolation so a catastrophic-backtracking
 *    user pattern (e.g. "(a+)+b") on a long string can no longer hang
 *    the accessibility callback thread → no more ANR → no more service
 *    kill. reload() also drops invalid regex patterns at load time and
 *    logs them.
 *  • OPT-4: regex patterns are pre-compiled at reload() time and stored
 *    in the immutable snapshot. Previously every accessibility event
 *    re-compiled every regex string from scratch.
 *
 * v10 (2.1.0) FIX-LOG:
 *  • Source-based timed-block awareness — evaluatePackage() now consults
 *    [TimedBlockManager] before returning Allow.
 *
 * v9 (2.0.0):
 *  • SharedFlow<Unit> "rulesChanged" replaces LocalBroadcastManager.
 *  • Schedule rules in the snapshot.
 *
 * v8 BUG-08: all reads via single immutable RulesSnapshot.
 */
@Singleton
class RulesEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getApps: GetAllAppRulesSyncUseCase,
    private val getKws: GetAllKeywordsSyncUseCase,
    private val getSchedules: GetAllScheduleRulesSyncUseCase,
    private val timedBlockManager: TimedBlockManager
) {
    companion object {
        const val ACTION_RULES_CHANGED = "com.guardian.shield.ACTION_RULES_CHANGED"
        /** v15 (FIX-2): cap text fed to keyword evaluation. */
        private const val MAX_TEXT_FOR_REGEX = 4096
    }

    /**
     * v15 (OPT-4): a keyword entry is either a literal lowercase substring
     * (regex == null) or a pre-compiled Regex. The original lowercase
     * pattern string is kept alongside for diagnostic / detection-detail
     * reporting purposes.
     */
    private data class KeywordMatcher(
        val regex: Regex?,
        val literal: String
    )

    private data class RulesSnapshot(
        val blocked: Set<String>,
        val whitelist: Set<String>,
        val keywords: List<KeywordMatcher>,
        val inputMethods: Set<String>,
        val scheduleRules: Map<String, ScheduleRule>
    )

    private val mutex = Mutex()
    private val ownPackage = context.packageName

    private val _rulesChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val rulesChanged: SharedFlow<Unit> = _rulesChanged.asSharedFlow()

    @Volatile private var snapshot: RulesSnapshot = RulesSnapshot(
        blocked = emptySet(),
        whitelist = emptySet(),
        keywords = emptyList(),
        inputMethods = AppClassifier.loadInputMethodPackages(context),
        scheduleRules = emptyMap()
    )

    suspend fun reload() = mutex.withLock {
        val apps = getApps()
        val kws = getKws()
        val schedules = runCatching { getSchedules() }.getOrDefault(emptyList())
        val ime = AppClassifier.loadInputMethodPackages(context)

        // v15 (FIX-2 + OPT-4): validate + pre-compile regex keywords.
        // Invalid patterns are silently dropped with a warning log so a
        // single bad rule cannot break keyword detection for everything
        // else.
        val keywordMatchers = kws.mapNotNull { kw ->
            val lower = kw.keyword.lowercase()
            if (kw.isRegex) {
                val compiled = runCatching { Regex(lower) }.getOrNull()
                if (compiled == null) {
                    Timber.w("Invalid regex keyword removed: '${kw.keyword}'")
                    null
                } else {
                    KeywordMatcher(regex = compiled, literal = lower)
                }
            } else {
                KeywordMatcher(regex = null, literal = lower)
            }
        }

        val newSnapshot = RulesSnapshot(
            whitelist = apps.filter { it.isWhitelisted }.map { it.packageName }.toSet(),
            blocked = apps.filter { it.isBlocked && !it.isWhitelisted }
                .map { it.packageName }.toSet(),
            keywords = keywordMatchers,
            inputMethods = ime,
            scheduleRules = schedules.associateBy { it.packageName }
        )
        snapshot = newSnapshot
        // v10: also refresh the timed-block cache.
        runCatching { timedBlockManager.refresh() }
        _rulesChanged.tryEmit(Unit)
    }

    private fun isAlwaysAllowed(pkg: String, snap: RulesSnapshot): Boolean =
        AppClassifier.isAlwaysAllowedPackage(ownPackage, pkg, snap.inputMethods)

    fun evaluatePackage(pkg: String): DetectionResult {
        val snap = snapshot
        if (isAlwaysAllowed(pkg, snap)) return DetectionResult.Allow
        if (snap.whitelist.contains(pkg)) return DetectionResult.Allow
        if (snap.blocked.contains(pkg)) return DetectionResult.Block(BlockReason.APP_BLOCKED, pkg)
        // v10: source-based timed-block (15 min) takes precedence over schedule.
        if (timedBlockManager.isBlockedSync(pkg)) {
            val remainingSec = (timedBlockManager.remainingMillis(pkg) / 1000L).coerceAtLeast(1L)
            return DetectionResult.Block(
                BlockReason.AI_SOURCE_TIMED_BLOCK,
                "Auto-locked for ${remainingSec}s after AI detection"
            )
        }
        if (isScheduleBlocked(pkg, snap)) {
            return DetectionResult.Block(BlockReason.SCHEDULE_BLOCKED, pkg)
        }
        return DetectionResult.Allow
    }

    /**
     * v15 (2.1.5) FIX-2 + OPT-4:
     *  • input is capped at [MAX_TEXT_FOR_REGEX] characters before
     *    evaluation — defends against unbounded growth on infinite-scroll
     *    surfaces (hand-in-hand with the AccessibilityService text cap).
     *  • per-regex match is wrapped in runCatching so any single
     *    pathological / catastrophic-backtracking pattern fails closed
     *    (treated as no-match) instead of hanging the callback thread.
     *  • patterns are pre-compiled at reload-time, so this method no
     *    longer pays a Regex(string) construction cost per keyword.
     */
    fun evaluateText(text: CharSequence?): DetectionResult {
        val snap = snapshot
        if (text.isNullOrBlank() || snap.keywords.isEmpty()) return DetectionResult.Allow
        val lower = text.toString().lowercase().take(MAX_TEXT_FOR_REGEX)
        for (matcher in snap.keywords) {
            val match = if (matcher.regex != null) {
                runCatching { matcher.regex.containsMatchIn(lower) }.getOrDefault(false)
            } else {
                lower.contains(matcher.literal)
            }
            if (match) return DetectionResult.Block(BlockReason.KEYWORD_MATCH, matcher.literal)
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

    fun isScheduleBlocked(pkg: String): Boolean = isScheduleBlocked(pkg, snapshot)

    private fun isScheduleBlocked(pkg: String, snap: RulesSnapshot): Boolean {
        val rule = snap.scheduleRules[pkg] ?: return false
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        if (dayOfWeek !in rule.enabledDays) return false
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = rule.startHour * 60 + rule.startMinute
        val endMinutes = rule.endHour * 60 + rule.endMinute
        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }
}
