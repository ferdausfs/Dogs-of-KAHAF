package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.ScheduleRule
import com.guardian.shield.domain.repository.RulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repo: RulesRepository,
    private val prefs: GuardianPreferences
) : ViewModel() {

    val rules: StateFlow<List<ScheduleRule>> = repo.observeSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(rule: ScheduleRule) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.upsertSchedule(rule)
                prefs.bumpRulesVersion()
            } catch (t: Throwable) {
                Timber.e(t, "Failed to save schedule rule — rules version NOT bumped; RulesEngine snapshot may stay stale")
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.deleteSchedule(id)
                prefs.bumpRulesVersion()
            } catch (t: Throwable) {
                Timber.e(t, "Failed to delete schedule rule — rules version NOT bumped; RulesEngine snapshot may stay stale")
            }
        }
    }
}
