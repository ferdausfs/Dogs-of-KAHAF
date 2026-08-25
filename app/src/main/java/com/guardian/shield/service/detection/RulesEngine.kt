package com.guardian.shield.service.detection

import android.content.Context
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.DetectionResult
import com.guardian.shield.domain.model.ScheduleRule
import com.guardian.shield.domain.repository.RulesRepository
import com.guardian.shield.util.AppClassifier
import com.guardian.shield.util.DefaultSystemWhitelist
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
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
    // R7.7 — multi-window: one package may hold several schedule windows,
    // so the snapshot groups rules by package instead of keeping one-per-app.
    val scheduleRules: Map<String, List<ScheduleRule>> = emptyMap(),
    // R4 — Focus Mode: epoch-ms until which every non-whitelisted user-facing
    // app is blocked. Time check happens fresh at evaluation (like schedules),
    // so expiry needs no rules reload.
    val focusUntilMs: Long = 0L
)

@Singleton
class RulesEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: RulesRepository,
    private val prefs: GuardianPreferences
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
                val sched = repo.allSchedules().groupBy { it.packageName }
                val focusUntil = runCatching { prefs.focusUntilMs.first() }.getOrDefault(0L)
                snapshot = RulesSnapshot(blocked, whitelist, kws, imes, sched, focusUntil)
                _rulesChanged.tryEmit(Unit)
                Timber.d(
                    "Rules reloaded: blocked=${blocked.size} whitelist=${whitelist.size} " +
                        "keywords=${kws.size} schedules=${sched.size} focusUntil=$focusUntil"
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

    /**
     * Gate used by every scan/block path (a11y window/content handlers,
     * periodic scanner, AI screenshot callback). Returning false means
     * "never scan, never block this package".
     *
     * Priority (mirrors [evaluatePackage]; see DefaultSystemWhitelist doc):
     *   1. always-allowed layer (self/SystemUI/home/IME/...)   -> false
     *   2. user whitelist                                       -> false
     *   3. user EXPLICIT BLOCK                                  -> TRUE
     *   4. user ENABLED SCHEDULE rule exists                    -> TRUE
     *   5. DefaultSystemWhitelist (system/OEM, no user rule)    -> false
     *   6. otherwise                                            -> true
     *
     * #3/#4 must outrank #5 — the caller checks canBlock() BEFORE
     * evaluatePackage(), so if the default whitelist gated a user-rule
     * package here, the user's own block/schedule would be unreachable.
     */
    private fun isFocusActive(s: RulesSnapshot): Boolean =
        s.focusUntilMs > System.currentTimeMillis()

    fun canBlock(pkg: String): Boolean {
        val s = snapshot
        if (AppClassifier.isAlwaysAllowedPackage(ownPkg, pkg, s.inputMethods)) return false
        if (s.whitelist.contains(pkg)) return false
        // R4 — Focus Mode: during an active session every non-whitelisted,
        // non-system-critical package is scannable/blockable — including
        // DefaultSystemWhitelist ones (the user explicitly asked for quiet).
        if (isFocusActive(s)) return true
        // User-explicit rules beat the default system/OEM whitelist (#3/#4).
        // (For non-system packages these early returns change nothing — the
        // function would reach `return true` anyway.)
        if (s.blocked.contains(pkg)) return true
        if (s.scheduleRules[pkg]?.any { it.enabled } == true) return true
        val defaultReason = DefaultSystemWhitelist.matchReason(pkg)
        if (defaultReason != null) {
            // Distinct from (silent) user-whitelist skips — diagnosable in logcat.
            DefaultSystemWhitelist.logSkipOnce(pkg, defaultReason)
            return false
        }
        return true
    }

    /**
     * Full package verdict. Same priority as [canBlock]:
     * user whitelist > user explicit block > user schedule (in-window) >
     * DefaultSystemWhitelist > normal Allow. Also called directly (without
     * the canBlock gate) by NotificationShieldService, so the default
     * whitelist MUST short-circuit to Allow here too — a system/OEM package
     * without user rules can never produce Block from this function.
     */
    fun evaluatePackage(pkg: String): DetectionResult {
        val s = snapshot
        if (AppClassifier.isAlwaysAllowedPackage(ownPkg, pkg, s.inputMethods)) return DetectionResult.Allow
        if (s.whitelist.contains(pkg)) return DetectionResult.Allow
        // R4 — Focus Mode short-circuit: whole-device temporary pause. Uses
        // SCHEDULE_BLOCKED (time-based) so overlays/logs need no new reason.
        if (isFocusActive(s)) {
            return DetectionResult.Block(BlockReason.SCHEDULE_BLOCKED, FOCUS_DETAIL)
        }
        if (s.blocked.contains(pkg)) {
            // User block wins over the default system/OEM whitelist (explicit
            // rule > safety default). Warn-level once per package so an
            // intentional-but-risky block of e.g. a settings/store app is
            // visible in diagnostics.
            val overridden = DefaultSystemWhitelist.matchReason(pkg)
            if (overridden != null) {
                Timber.w(
                    "User rule OVERRIDES DefaultSystemWhitelist for %s (%s) — explicit user block is honored",
                    pkg, overridden
                )
            }
            return DetectionResult.Block(BlockReason.APP_BLOCKED, pkg)
        }
        if (isScheduleBlocked(pkg)) {
            return DetectionResult.Block(BlockReason.SCHEDULE_BLOCKED, pkg)
        }
        val defaultReason = DefaultSystemWhitelist.matchReason(pkg)
        if (defaultReason != null) {
            DefaultSystemWhitelist.logSkipOnce(pkg, defaultReason)
            return DetectionResult.Allow
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
        val rules = snapshot.scheduleRules[pkg] ?: return false
        val cal = Calendar.getInstance()
        val dayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6) // 0=Sun..6=Sat
        val nowMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        // R7.7 — blocked when ANY enabled window of this package matches now.
        return rules.any { rule ->
            if (!rule.enabled || !rule.enabledDays.contains(dayOfWeek)) false
            else {
                val start = rule.startHour * 60 + rule.startMinute
                val end = rule.endHour * 60 + rule.endMinute
                if (start <= end) nowMins in start until end
                else nowMins >= start || nowMins < end
            }
        }
    }

    private companion object {
        // Unicode word character (letters, numbers, underscore). Java's \b is
        // ASCII-only, so word boundaries for Bengali/other scripts must be
        // expressed as lookarounds.
        const val BEFORE_WORD = "(?<![\\p{L}\\p{N}_])"
        const val AFTER_WORD = "(?![\\p{L}\\p{N}_])"

        // R4 — detail string attached to Focus Mode blocks (shown in overlays
        // and the activity log as the blocked-by text).
        const val FOCUS_DETAIL = "Focus Mode"
    }
}
