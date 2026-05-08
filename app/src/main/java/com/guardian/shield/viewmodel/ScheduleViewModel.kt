package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.ScheduleRule
import com.guardian.shield.domain.usecase.DeleteScheduleRuleUseCase
import com.guardian.shield.domain.usecase.GetScheduleRulesUseCase
import com.guardian.shield.domain.usecase.UpsertScheduleRuleUseCase
import com.guardian.shield.service.detection.RulesEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v9 (2.0.0) — P4-A: ViewModel for time-based schedule rules.
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    getRules: GetScheduleRulesUseCase,
    private val upsertUC: UpsertScheduleRuleUseCase,
    private val deleteUC: DeleteScheduleRuleUseCase,
    private val prefs: GuardianPreferences,
    private val rulesEngine: RulesEngine
) : ViewModel() {

    val rules: StateFlow<List<ScheduleRule>> = getRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(rule: ScheduleRule) = viewModelScope.launch {
        runCatching { upsertUC(rule) }
        runCatching { prefs.bumpRulesVersion() }
        runCatching { rulesEngine.reload() }
    }

    fun delete(packageName: String) = viewModelScope.launch {
        runCatching { deleteUC(packageName) }
        runCatching { prefs.bumpRulesVersion() }
        runCatching { rulesEngine.reload() }
    }
}
