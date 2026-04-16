package com.kahaf.guardian.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardian.domain.model.ProtectionState
import com.kahaf.guardian.domain.repository.AppRepository
import com.kahaf.guardian.domain.repository.BlockLogRepository
import com.kahaf.guardian.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val blockLog: BlockLogRepository,
    private val appRepo: AppRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ProtectionState())
    val protectionState: StateFlow<ProtectionState> = _state.asStateFlow()
    private val _isPinSet = MutableStateFlow(false)
    val isPinSet: StateFlow<Boolean> = _isPinSet.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settings.isProtectionActive(), blockLog.getTodayBlockCount(),
                blockLog.getTotalBlockCount(), settings.isKeywordDetectionEnabled(),
                settings.isAiDetectionEnabled()) { a, t, tt, k, ai ->
                ProtectionState(a, t, tt, isKeywordDetectionEnabled = k, isAiDetectionEnabled = ai)
            }.collect { _state.value = it }
        }
        viewModelScope.launch { _isPinSet.value = settings.isPinSet() }
    }

    fun toggleProtection(active: Boolean) { viewModelScope.launch { settings.setProtectionActive(active) } }
    fun getBlockedAppsCount(): Flow<Int> = appRepo.getBlockedApps().map { it.size }
    fun getWhitelistedAppsCount(): Flow<Int> = appRepo.getWhitelistedApps().map { it.size }
}
