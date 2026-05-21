package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.repository.RulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val ACTIVITY_LOG_LIMIT = 250

enum class ActivityLogFilter {
    ALL,
    AI,
    KEYWORD,
    APP,
    SCHEDULE
}

data class ActivityLogUiState(
    val allEvents: List<BlockEvent> = emptyList(),
    val visibleEvents: List<BlockEvent> = emptyList(),
    val filter: ActivityLogFilter = ActivityLogFilter.ALL,
    val query: String = ""
)

@HiltViewModel
class ActivityLogViewModel @Inject constructor(
    private val repo: RulesRepository
) : ViewModel() {

    private val filter = MutableStateFlow(ActivityLogFilter.ALL)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<ActivityLogUiState> = combine(
        repo.observeEvents(ACTIVITY_LOG_LIMIT),
        filter,
        query
    ) { events, selectedFilter, searchQuery ->
        val normalizedQuery = searchQuery.trim().lowercase()
        val filtered = events.filter { event ->
            matchesFilter(event, selectedFilter) && matchesQuery(event, normalizedQuery)
        }

        ActivityLogUiState(
            allEvents = events,
            visibleEvents = filtered,
            filter = selectedFilter,
            query = searchQuery
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityLogUiState()
    )

    fun setFilter(value: ActivityLogFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.update { value }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.deleteEvent(id) }
        }
    }

    private fun matchesFilter(event: BlockEvent, selectedFilter: ActivityLogFilter): Boolean {
        return when (selectedFilter) {
            ActivityLogFilter.ALL -> true
            ActivityLogFilter.AI -> event.reason == BlockReason.AI_DETECTION
            ActivityLogFilter.KEYWORD -> event.reason == BlockReason.KEYWORD_MATCH
            ActivityLogFilter.APP -> event.reason == BlockReason.APP_BLOCKED || event.reason == BlockReason.MANUAL
            ActivityLogFilter.SCHEDULE -> event.reason == BlockReason.SCHEDULE_BLOCKED
        }
    }

    private fun matchesQuery(event: BlockEvent, normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return true
        return buildString {
            append(event.packageName)
            append(' ')
            append(event.reason.name)
            append(' ')
            append(event.matchedTerm.orEmpty())
        }.lowercase().contains(normalizedQuery)
    }
}
