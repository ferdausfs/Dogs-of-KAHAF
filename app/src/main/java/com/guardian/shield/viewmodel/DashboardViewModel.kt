package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * v9 (2.0.0):
 *  • P4-B → exposes [todayStats] StateFlow with totalBlocks / aiBlocks /
 *    keywordBlocks / topApp aggregated for the dashboard summary card.
 *  • P4-C → exposes [protectionEnabled] + toggleProtection() for the FAB.
 *  • P4-D → getAllEvents() returns the full list for CSV export.
 */
data class BlockStats(
    val totalBlocks: Int = 0,
    val aiBlocks: Int = 0,
    val keywordBlocks: Int = 0,
    val appBlocks: Int = 0,
    val scheduleBlocks: Int = 0,
    val topApp: String? = null,
    val topAppCount: Int = 0
)

data class DashboardUi(
    val recent: List<BlockEvent> = emptyList(),
    val todayCount: Int = 0,
    val protectionActive: Boolean = false,
    val protectionEnabled: Boolean = true,
    val stats: BlockStats = BlockStats()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getEvents: GetBlockEventsUseCase,
    private val countToday: CountTodayBlocksUseCase,
    private val clearEvents: ClearBlockEventsUseCase,
    private val getAllEventsUC: GetAllBlockEventsUseCase,
    private val observeSinceUC: ObserveBlockEventsSinceUseCase,
    private val prefs: GuardianPreferences
) : ViewModel() {

    private val _ui = MutableStateFlow(DashboardUi())
    val ui: StateFlow<DashboardUi> = _ui.asStateFlow()

    /** P4-B: live-aggregated stats for today, exposed as a StateFlow. */
    val todayStats: StateFlow<BlockStats> = observeSinceUC(todayMidnightMs())
        .map { events -> aggregate(events) }
        .stateIn(viewModelScope, SharingStarted.Lazily, BlockStats())

    init {
        viewModelScope.launch {
            getEvents(50).collect { evts ->
                _ui.update { it.copy(recent = evts, todayCount = countToday()) }
            }
        }
        viewModelScope.launch {
            todayStats.collect { stats -> _ui.update { it.copy(stats = stats) } }
        }
        // P4-C: keep protection master switch in sync with DataStore.
        viewModelScope.launch {
            prefs.protectionEnabled.collect { v ->
                _ui.update { it.copy(protectionEnabled = v) }
            }
        }
    }

    fun setProtectionActive(active: Boolean) = _ui.update { it.copy(protectionActive = active) }
    fun clearAll() = viewModelScope.launch { clearEvents() }

    /** P4-C: toggle the master protection switch (FAB). */
    fun toggleProtection() = viewModelScope.launch {
        val curr = prefs.currentProtectionEnabled()
        prefs.setProtectionEnabled(!curr)
    }

    /** P4-D: full block-event list for CSV export. */
    suspend fun getAllEvents(): List<BlockEvent> = getAllEventsUC()

    private fun aggregate(events: List<BlockEvent>): BlockStats {
        if (events.isEmpty()) return BlockStats()
        val ai = events.count { it.reason == BlockReason.AI_DETECTION }
        val kw = events.count { it.reason == BlockReason.KEYWORD_MATCH }
        val app = events.count { it.reason == BlockReason.APP_BLOCKED }
        val sched = events.count { it.reason == BlockReason.SCHEDULE_BLOCKED }
        val topPair = events.groupingBy { it.packageName }.eachCount()
            .maxByOrNull { it.value }
        return BlockStats(
            totalBlocks = events.size,
            aiBlocks = ai,
            keywordBlocks = kw,
            appBlocks = app,
            scheduleBlocks = sched,
            topApp = topPair?.key,
            topAppCount = topPair?.value ?: 0
        )
    }

    private fun todayMidnightMs(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
