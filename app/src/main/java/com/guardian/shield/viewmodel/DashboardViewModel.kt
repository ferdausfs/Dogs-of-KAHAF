package com.guardian.shield.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.AppRule
import com.guardian.shield.domain.repository.RulesRepository
import com.guardian.shield.util.PermissionManager
import com.guardian.shield.util.ScreenTimeTracker
import com.guardian.shield.util.StreakCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

data class BlockStats(
    val totalBlocks: Int = 0,
    val aiBlocks: Int = 0,
    val keywordBlocks: Int = 0,
    val topApp: String? = null
)

data class DashboardUiState(
    val recent: List<BlockEvent> = emptyList(),
    val todayCount: Int = 0,
    val protectionActive: Boolean = false,
    val protectionEnabled: Boolean = true,
    val stats: BlockStats = BlockStats()
)

/** R7.4 — real screen-time slice for the dashboard (usage-access powered). */
data class ScreenTimeUiState(
    val granted: Boolean = false,
    val totalTodayMs: Long = 0L,
    val suggestion: ScreenTimeTracker.AppUsage? = null,
    val suggestionBlocked: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: RulesRepository,
    private val prefs: GuardianPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _protectionActive = MutableStateFlow(false)

    private val todayStart: Long get() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        repo.observeEvents(20),
        repo.countSinceFlow(todayStart),
        repo.countByReasonFlow(todayStart, BlockReason.AI_DETECTION),
        repo.countByReasonFlow(todayStart, BlockReason.KEYWORD_MATCH),
        repo.topPackageFlow(todayStart),
        prefs.protectionEnabled,
        _protectionActive
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val recent = values[0] as List<BlockEvent>
        val count = values[1] as Int
        val ai = values[2] as Int
        val kw = values[3] as Int
        val top = values[4] as String?
        val enabled = values[5] as Boolean
        val active = values[6] as Boolean
        DashboardUiState(
            recent = recent,
            todayCount = count,
            protectionActive = active,
            protectionEnabled = enabled,
            stats = BlockStats(count, ai, kw, top)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    // PHASE 3 (v3.5.0) — clean-streak + weekly comparison, computed read-only
    // from the existing block_events history. The 400-day load window is a
    // pragmatic cap: a streak only miscounts if the user's most recent strike-3
    // full block is >400 days old (i.e. a 400+ day streak, which then simply
    // keeps counting — direction stays correct). The install-day floor keeps a
    // zero-block user's streak bounded to "days since install".
    private val streakWindowStart: Long =
        System.currentTimeMillis() - 400L * 24 * 60 * 60 * 1_000L

    private val installDayStart: Long = StreakCalculator.localDayStart(
        runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).firstInstallTime
        }.getOrDefault(0L)
    )

    val streakInfo: StateFlow<StreakCalculator.StreakInfo> =
        repo.observeEventsSince(streakWindowStart)
            .map { StreakCalculator.compute(it, floorDayStart = installDayStart) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                StreakCalculator.StreakInfo()
            )

    fun setProtectionActive(active: Boolean) { _protectionActive.value = active }

    // ---- R7.4 screen time (usage stats are pull-based, no flow — refresh
    //      from the fragment's onResume) ---------------------------------
    private val _screenTime = MutableStateFlow(ScreenTimeUiState())
    val screenTime: StateFlow<ScreenTimeUiState> = _screenTime.asStateFlow()

    fun refreshScreenTime() {
        viewModelScope.launch(Dispatchers.IO) {
            val granted = PermissionManager.isUsageStatsGranted(appContext)
            if (!granted) {
                _screenTime.value = ScreenTimeUiState(granted = false)
                return@launch
            }
            val summary = ScreenTimeTracker.summary(appContext, topN = 1)
            val top = summary.top.firstOrNull()
            val blocked = top?.let { pkg ->
                runCatching { repo.getApp(pkg.packageName)?.isBlocked == true }
                    .getOrDefault(false)
            } ?: false
            _screenTime.value = ScreenTimeUiState(
                granted = true,
                totalTodayMs = summary.totalMs,
                suggestion = top?.takeIf {
                    it.totalMs >= ScreenTimeTracker.SUGGEST_AFTER_MS && !blocked
                },
                suggestionBlocked = blocked
            )
        }
    }

    /** One-tap block straight from the dashboard suggestion card. */
    fun blockSuggestedApp() {
        val target = _screenTime.value.suggestion ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val current = repo.getApp(target.packageName)
                val updated = (current ?: AppRule(
                    target.packageName, target.label, false, false,
                    System.currentTimeMillis()
                )).copy(isBlocked = true, isWhitelisted = false)
                repo.upsertApp(updated)
                prefs.bumpRulesVersion()
            }
            refreshScreenTime()
        }
    }

    // R4 — REAL sparkline data: today's block events bucketed per hour (24
    // buckets). Read-only over block_events; starts from today's local
    // midnight at VM creation.
    val hourlyToday: StateFlow<List<Int>> = repo.observeEventsSince(todayStart)
        .map { events ->
            val buckets = IntArray(24)
            val cal = Calendar.getInstance()
            events.forEach {
                cal.timeInMillis = it.timestamp
                buckets[cal.get(Calendar.HOUR_OF_DAY)]++
            }
            buckets.toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(24) { 0 })

    fun toggleProtection() {
        viewModelScope.launch {
            val current = prefs.protectionEnabled.first()
            prefs.setProtectionEnabled(!current)
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            try { repo.clearEvents() } catch (_: Throwable) {}
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try { repo.deleteEvent(id) } catch (_: Throwable) {}
        }
    }

    suspend fun getAllEvents(): List<BlockEvent> = withContext(Dispatchers.IO) {
        try { repo.allEvents() } catch (_: Throwable) { emptyList() }
    }

    /** R7.6 — weekly digest text for the dashboard share action. */
    suspend fun weeklyReportText(): String = withContext(Dispatchers.IO) {
        com.guardian.shield.util.WeeklyReporter.fetchReportText(appContext, repo)
    }
}
