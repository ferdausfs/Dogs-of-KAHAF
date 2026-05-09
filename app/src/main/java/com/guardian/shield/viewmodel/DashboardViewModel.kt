package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * v17 (2.1.7) BUILD-FIX:
 *  • Kotlin coroutines 1.8.x makes `distinctUntilChanged()` on a StateFlow
 *    a hard compile error (StateFlow already de-dupes by equals).
 *    Fix: upcast `midnightTrigger` to a plain `Flow<Long>` first, so
 *    `distinctUntilChanged()` resolves to the regular Flow extension.
 *    Behaviour preserved: flatMapLatest will still only re-subscribe on
 *    actual midnight rollovers.
 *  • Wildcard `kotlinx.coroutines.flow.*` import replaced with explicit
 *    imports — avoids accidentally importing the deprecated
 *    `StateFlow.distinctUntilChanged` overload.
 *  • Added @OptIn(ExperimentalCoroutinesApi::class) for `flatMapLatest`
 *    locally — defensive even though the project also opts-in globally.
 *
 * v15 (2.1.5) STABILITY PATCH 5:
 *  • OPT-2: removed the per-emission countToday() DB query. todayCount is
 *    now driven directly by the [todayStats] StateFlow (which already
 *    aggregates totalBlocks from observeSinceUC). On a busy block-flow
 *    this saves one full COUNT(*) round-trip per Room emission.
 *
 * v13 (2.1.3) STABILITY PATCH 3:
 *  • todayStats no longer freezes at the midnight captured at construction
 *    time. The boundary is recomputed via a flatMapLatest on a tick flow
 *    (`midnightTrigger`) that re-emits when the system date crosses
 *    midnight.
 *  • toggleProtection() uses a @Volatile cache of the latest emission
 *    instead of a 2 s-bounded DataStore read.
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getEvents: GetBlockEventsUseCase,
    private val clearEvents: ClearBlockEventsUseCase,
    private val getAllEventsUC: GetAllBlockEventsUseCase,
    private val observeSinceUC: ObserveBlockEventsSinceUseCase,
    private val prefs: GuardianPreferences
) : ViewModel() {

    private val _ui = MutableStateFlow(DashboardUi())
    val ui: StateFlow<DashboardUi> = _ui.asStateFlow()

    @Volatile private var protectionEnabledCache: Boolean = true

    private val midnightTrigger = MutableStateFlow(todayMidnightMs())

    /**
     * v16 (2.1.6) NEW-OPT-1: only re-subscribe to the Room flow when the
     * midnight boundary actually changes (i.e. once a day). Without
     * distinctUntilChanged() rapid setProtectionActive() calls (e.g.
     * accessibility toggle) re-emit the SAME midnight timestamp, and
     * flatMapLatest cancels + restarts the observeSinceUC subscription on
     * each emission. Room's InvalidationTracker on API 26 occasionally
     * threw "attempt to re-open an already-closed object" on rapid
     * re-subscribe — fixed here.
     */
    val todayStats: StateFlow<BlockStats> = (midnightTrigger as Flow<Long>)
        .distinctUntilChanged()
        .flatMapLatest { since -> observeSinceUC(since).map { aggregate(it) } }
        .stateIn(viewModelScope, SharingStarted.Lazily, BlockStats())

    init {
        viewModelScope.launch {
            getEvents(50).collect { evts ->
                // v15 (OPT-2): todayCount is now driven by todayStats below.
                _ui.update { it.copy(recent = evts) }
                refreshMidnightIfRolledOver()
            }
        }
        viewModelScope.launch {
            // v15 (OPT-2): drive both stats AND todayCount from the same
            // aggregated flow → one source of truth, one query.
            todayStats.collect { stats ->
                _ui.update { it.copy(stats = stats, todayCount = stats.totalBlocks) }
            }
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
        refreshMidnightIfRolledOver()
    }

    fun clearAll() = viewModelScope.launch { clearEvents() }

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
