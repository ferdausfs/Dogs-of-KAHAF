package com.kahaf.guardianshield.presentation.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardianshield.domain.model.DaysMask
import com.kahaf.guardianshield.domain.model.InstalledApp
import com.kahaf.guardianshield.domain.model.Schedule
import com.kahaf.guardianshield.domain.repository.AppRuleRepository
import com.kahaf.guardianshield.domain.repository.ScheduleRepository
import com.kahaf.guardianshield.service.timed.TimedBlockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SchedulesViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val appRuleRepository: AppRuleRepository,
    private val timedBlockManager: TimedBlockManager
) : ViewModel() {

    val schedules: StateFlow<List<Schedule>> = scheduleRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val allApps: StateFlow<List<InstalledApp>> = _allApps.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _allApps.value = appRuleRepository.getInstalledApps(includeSystem = false)
        }
    }

    fun upsert(schedule: Schedule) {
        viewModelScope.launch {
            scheduleRepository.upsert(schedule)
            timedBlockManager.recompute()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            scheduleRepository.delete(id)
            timedBlockManager.recompute()
        }
    }

    fun emptySchedule(): Schedule = Schedule(
        id = 0L,
        label = "New schedule",
        daysMask = DaysMask.ALL,
        startMin = 22 * 60,
        endMin = 6 * 60,
        packages = emptyList(),
        enabled = true
    )
}
