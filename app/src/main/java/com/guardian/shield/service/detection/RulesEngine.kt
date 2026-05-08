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
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v9 (2.0.0) FIX-LOG:
 *  • P2-B → expose a SharedFlow<Unit> "rulesChanged" instead of relying on
 *    LocalBroadcastManager. GuardianAccessibilityService collects it directly
 *    via coroutine, removing the deprecated AndroidX dependency.
 *  • P4-A → schedule rules are part of the snapshot. isScheduleBlocked(pkg)
 *    returns true if NOW falls inside a per-package time window (with
 *    overnight wrap support) on an enabled day.
 *
 * Earlier v8 BUG-08 still applies: all read paths go through a single
 * immutable [RulesSnapshot] replaced atomically.
 */
@Singleton
class RulesEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getApps: GetAllAppRulesSyncUseCase,
    private val getKws: GetAllKeywordsSyncUseCase,
    private val getSchedules: GetAllScheduleRulesSyncUseCase
) {
    companion object {
        // Kept for back-compat in case any external code still references it.
        const val ACTION_RULES_CHANGED = "com.guardian.shield.ACTION_RULES_CHANGED"
    }

    /** Atomic, fully-coherent view of all rules. Replaced as one reference. */
    private data class RulesSnapshot(
        val blocked: Set<String>,
        val whitelist: Set<String>,
        val keywords: List<Pair<String, Boolean>>,
        val inputMethods: Set<String>,
        val scheduleRules: Map<String, ScheduleRule>
    )

    private val mutex = Mutex()
    private val ownPackage = context.packageName

    // P2-B: external observers use this flow instead of LocalBroadcastManager.
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

        // Build the new snapshot completely BEFORE swapping the volatile
        // reference — readers either see the fully-old snapshot or the
        // fully-new one, never a mix.
        val newSnapshot = RulesSnapshot(
            whitelist = apps.filter { it.isWhitelisted }.map { it.packageName }.toSet(),
            blocked = apps.filter { it.isBlocked && !it.isWhitelisted }
                .map { it.packageName }.toSet(),
            keywords = kws.map { it.keyword.lowercase() to it.isRegex },
            inputMethods = ime,
            scheduleRules = schedules.associateBy { it.packageName }
        )
        snapshot = newSnapshot
        // P2-B: notify observers (replaces LocalBroadcastManager).
        _rulesChanged.tryEmit(Unit)
    }

    private fun isAlwaysAllowed(pkg: String, snap: RulesSnapshot): Boolean =
        AppClassifier.isAlwaysAllowedPackage(ownPackage, pkg, snap.inputMethods)

    fun evaluatePackage(pkg: String): DetectionResult {
        val snap = snapshot
        if (isAlwaysAllowed(pkg, snap)) return DetectionResult.Allow
        if (snap.whitelist.contains(pkg)) return DetectionResult.Allow
        if (snap.blocked.contains(pkg)) return DetectionResult.Block(BlockReason.APP_BLOCKED, pkg)
        // P4-A: schedule-based blocking (e.g. social media 22:00–06:00).
        if (isScheduleBlocked(pkg, snap)) {
            return DetectionResult.Block(BlockReason.SCHEDULE_BLOCKED, pkg)
        }
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

    /**
     * P4-A: returns true if [pkg] currently falls inside its scheduled-block
     * time window. Supports overnight ranges (e.g. start=22:00, end=06:00).
     * Days are 0-indexed where 0 = Sunday (matches Calendar.DAY_OF_WEEK - 1).
     */
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
            // Overnight wrap: e.g. 22:00–06:00.
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }
}
