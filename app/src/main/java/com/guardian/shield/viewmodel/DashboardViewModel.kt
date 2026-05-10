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
 * v13 (2.1.3) STABILITY PATCH 3:
 *  • CRITICAL FIX: todayStats no longer freezes at the midnight captured
 *    when the ViewModel was constructed. The boundary is recomputed via a
 *    flatMapLatest on a tick flow (`midnightTrigger`) that re-emits when
 *    the system date crosses midnight.
 *  • CRITICAL FIX: toggleProtection() no longer reads via the 2 s-bounded
 *    currentProtectionEnabled() — that could default to `true` on a slow
 *    DataStore and incorrectly toggle. We now keep a @Volatile cache of
 *    the latest emitted value and toggle off that.
 *
 * v9 (2.0.0):
 *  • P4-B / P4-C / P4-D — kept as-is.
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

    /**
     * v13: Volatile cache of the latest protectionEnabled emission so the
     * FAB toggle doesn't have to await a fresh DataStore read.
     */
    @Volatile private var protectionEnabledCache: Boolean = true

    /**
     * v13: re-emits whenever the system day rolls over so [todayStats]
     * recomputes its midnight boundary instead of staying stuck on the
     * boundary captured at ViewModel-init time.
     */
    private val midnightTrigger = MutableStateFlow(todayMidnightMs())

    /** v13: live-aggregated stats for *today*, with a self-refreshing window. */
    val todayStats: StateFlow<BlockStats> = midnightTrigger
        .flatMapLatest { since -> observeSinceUC(since).map { aggregate(it) } }
        .stateIn(viewModelScope, SharingStarted.Lazily, BlockStats())

    init {
        viewModelScope.launch {
            getEvents(50).collect { evts ->
                _ui.update { it.copy(recent = evts, todayCount = countToday()) }
                // Cheap opportunistic midnight check on every emission.
                refreshMidnightIfRolledOver()
            }
        }
        viewModelScope.launch {
            todayStats.collect { stats -> _ui.update { it.copy(stats = stats) } }
        }
        viewModelScope.launch {
            prefs.protectionEnabled.collect { v ->
                protectionEnabledCache = v
                _ui.update { it.copy(protectionEnabled = v) }
            }
        }
    }

    fun setProtectionActive(active: Boolean) {
        _ui.update { it.copy(protectionActive = active) }
        // Lifecycle-driven midnight refresh: the user reopening the app
        // after midnight will re-bucket "today".
        refreshMidnightIfRolledOver()
    }

    fun clearAll() = viewModelScope.launch { clearEvents() }

    /**
     * v13: toggles using the cached value instead of a bounded read that
     * could default to `true` and produce a wrong flip.
     */
    fun toggleProtection() = viewModelScope.launch {
        prefs.setProtectionEnabled(!protectionEnabledCache)
    }

    suspend fun getAllEvents(): List<BlockEvent> = getAllEventsUC()

    private fun refreshMidnightIfRolledOver() {
        val current = todayMidnightMs()
        if (current != midnightTrigger.value) {
            midnightTrigger.value = current
        }
    }

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
