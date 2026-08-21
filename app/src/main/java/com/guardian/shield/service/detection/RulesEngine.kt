package com.guardian.shield.service.detection

import android.content.Context
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.domain.model.ScheduleRule
import com.guardian.shield.domain.repository.RulesRepository
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

data class RulesSnapshot(
    val blocked: Set<String> = emptySet(),
    val whitelist: Set<String> = emptySet(),
    val keywords: List<Pair<String, Boolean>> = emptyList(),
    val inputMethods: Set<String> = emptySet(),
    val scheduleRules: Map<String, ScheduleRule> = emptyMap()
)

@Singleton
class RulesEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: RulesRepository
) {
    private val mutex = Mutex()

    @Volatile
    private var snapshot: RulesSnapshot = RulesSnapshot()

    private val _rulesChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val rulesChanged: SharedFlow<Unit> = _rulesChanged.asSharedFlow()

    private val ownPkg: String by lazy { context.packageName }

    suspend fun reload() {
        mutex.withLock {
            try {
                val blocked = repo.blockedPackages()
                val whitelist = repo.whitelistPackages()
                val kws = repo.enabledKeywords().map { it.keyword to it.isRegex }
                val imes = AppClassifier.loadInputMethodPackages(context)
                val sched = repo.allSchedules().associateBy { it.packageName }
                snapshot = RulesSnapshot(blocked, whitelist, kws, imes, sched)
                _rulesChanged.tryEmit(Unit)
                Timber.d(
                    "Rules reloaded: blocked=${blocked.size} whitelist=${whitelist.size} " +
                        "keywords=${kws.size} schedules=${sched.size}"
                )
            } catch (t: Throwable) {
                // v3.6.2 — surface exactly WHAT stayed stale so a frozen
                // snapshot (rules edits visible in the UI but ignored by
                // canBlock/evaluatePackage) is diagnosable from the log.
                val s = snapshot
                Timber.e(
                    t,
                    "Failed to reload rules — KEEPING STALE snapshot " +
                        "(blocked=${s.blocked.size}, whitelist=${s.whitelist.size}, " +
                        "keywords=${s.keywords.size}, schedules=${s.scheduleRules.size})"
                )
            }
        }
    }

    fun current(): RulesSnapshot = snapshot

    fun canBlock(pkg: String): Boolean {
        val s = snapshot
        if (AppClassifier.isAlwaysAllowedPackage(ownPkg, pkg, s.inputMethods)) return false
        if (s.whitelist.contains(pkg)) return false
        return true
    }

    fun evaluatePackage(pkg: String): DetectionResult {
        val s = snapshot
        if (AppClassifier.isAlwaysAllowedPackage(ownPkg, pkg, s.inputMethods)) return DetectionResult.Allow
        if (s.whitelist.contains(pkg)) return DetectionResult.Allow
        if (s.blocked.contains(pkg)) {
            return DetectionResult.Block(BlockReason.APP_BLOCKED, pkg)
        }
        if (isScheduleBlocked(pkg)) {
            return DetectionResult.Block(BlockReason.SCHEDULE_BLOCKED, pkg)
        }
        return DetectionResult.Allow
    }

    fun evaluateText(text: String): DetectionResult {
        if (text.isBlank() || text.length < 2) return DetectionResult.Allow
        val s = snapshot
        for ((kw, isRegex) in s.keywords) {
            try {
                if (isRegex) {
                    // FALSE-BLOCK FIX: wrap user regexes in word boundaries unless the
                    // user already anchors their own match, so a bare keyword like
                    // "sex" can't match inside "Essex"/"sextant". Regexes that already
                    // anchor with ^ $ or word-boundary \b keep their exact semantics.
                    val raw = kw.trim()
                    val alreadyAnchored = raw.contains('^') || raw.contains('$') ||
                        raw.startsWith("\\b") || raw.startsWith("\\B")
                    val pattern = if (alreadyAnchored) raw else "(?iu)$BEFORE_WORD(?:$raw)$AFTER_WORD"
                    val r = Regex(pattern, RegexOption.IGNORE_CASE)
                    if (r.containsMatchIn(text)) {
                        return DetectionResult.Block(BlockReason.KEYWORD_MATCH, kw)
                    }
                } else {
                    // Use a more efficient check. For keywords, ensure word boundaries
                    // to avoid false positives (e.g., "sex" matching "sextant").
                    // ASCII \b is NOT Unicode-aware, so Bengali/other non-Latin
                    // keywords could never match; use Unicode word-char lookarounds.
                    val pattern = "(?iu)$BEFORE_WORD${Regex.escape(kw)}$AFTER_WORD"
                    val matched = Regex(pattern).containsMatchIn(text)

                    if (matched) {
                        return DetectionResult.Block(BlockReason.KEYWORD_MATCH, kw)
                    }
                }
            } catch (_: Throwable) { /* invalid regex - skip */ }
        }
        return DetectionResult.Allow
    }

    private fun isScheduleBlocked(pkg: String): Boolean {
        val rule = snapshot.scheduleRules[pkg] ?: return false
        if (!rule.enabled) return false
        val cal = Calendar.getInstance()
        val dayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6) // 0=Sun..6=Sat
        if (!rule.enabledDays.contains(dayOfWeek)) return false
        val nowMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = rule.startHour * 60 + rule.startMinute
        val end = rule.endHour * 60 + rule.endMinute
        return if (start <= end) nowMins in start until end
        else nowMins >= start || nowMins < end
    }

    private companion object {
        // Unicode word character (letters, numbers, underscore). Java's \b is
        // ASCII-only, so word boundaries for Bengali/other scripts must be
        // expressed as lookarounds.
        const val BEFORE_WORD = "(?<![\\p{L}\\p{N}_])"
        const val AFTER_WORD = "(?![\\p{L}\\p{N}_])"
    }
}
