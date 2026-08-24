package com.guardian.shield.ui.activitylog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.repository.RulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LogFilter { ALL, AI, KEYWORD, APP, SCHEDULE }

data class ActivityLogUi(
    val events: List<BlockEvent> = emptyList(),
    val filter: LogFilter = LogFilter.ALL
)

@HiltViewModel
class ActivityLogViewModel @Inject constructor(
    private val repo: RulesRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(LogFilter.ALL)

    val uiState: StateFlow<ActivityLogUi> = combine(
        repo.observeEvents(limit = 500),
        _filter
    ) { events, filter ->
        val filtered = when (filter) {
            LogFilter.ALL -> events
            LogFilter.AI -> events.filter { it.reason == BlockReason.AI_DETECTION }
            LogFilter.KEYWORD -> events.filter { it.reason == BlockReason.KEYWORD_MATCH }
            LogFilter.APP -> events.filter { it.reason == BlockReason.APP_BLOCKED }
            LogFilter.SCHEDULE -> events.filter { it.reason == BlockReason.SCHEDULE_BLOCKED }
        }
        ActivityLogUi(events = filtered, filter = filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityLogUi())

    fun setFilter(filter: LogFilter) { _filter.value = filter }

    // R4 — REAL weekly bar data for the Reports card: current ISO week
    // (Monday-first, matching the M..S letter row in the layout), bucketed
    // per weekday. Read-only over block_events.
    private val isoWeekStart: Long get() {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val daysSinceMonday =
            (cal.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.MONDAY + 7) % 7
        cal.add(java.util.Calendar.DAY_OF_YEAR, -daysSinceMonday)
        return cal.timeInMillis
    }

    val weekCounts: StateFlow<List<Int>> = repo.observeEventsSince(isoWeekStart)
        .map { events ->
            val buckets = IntArray(7)
            val cal = java.util.Calendar.getInstance()
            events.forEach {
                cal.timeInMillis = it.timestamp
                buckets[(cal.get(java.util.Calendar.DAY_OF_WEEK) -
                    java.util.Calendar.MONDAY + 7) % 7]++
            }
            buckets.toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(7) { 0 })

    fun delete(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try { repo.deleteEvent(id) } catch (_: Throwable) {}
        }
    }
}
