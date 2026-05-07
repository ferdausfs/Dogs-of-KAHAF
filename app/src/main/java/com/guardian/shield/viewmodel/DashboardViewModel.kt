package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUi(
    val recent: List<BlockEvent> = emptyList(),
    val todayCount: Int = 0,
    val protectionActive: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getEvents: GetBlockEventsUseCase,
    private val countToday: CountTodayBlocksUseCase,
    private val clearEvents: ClearBlockEventsUseCase
) : ViewModel() {

    private val _ui = MutableStateFlow(DashboardUi())
    val ui: StateFlow<DashboardUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            getEvents(50).collect { evts ->
                _ui.update { it.copy(recent = evts, todayCount = countToday()) }
            }
        }
    }

    fun setProtectionActive(active: Boolean) = _ui.update { it.copy(protectionActive = active) }
    fun clearAll() = viewModelScope.launch { clearEvents() }
}
