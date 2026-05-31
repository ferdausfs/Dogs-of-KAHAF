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

    fun delete(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try { repo.deleteEvent(id) } catch (_: Throwable) {}
        }
    }
}
