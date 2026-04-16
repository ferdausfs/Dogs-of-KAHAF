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
    private val settingsRepository: SettingsRepository,
    private val blockLogRepository: BlockLogRepository,
    private val appRepository: AppRepository
) : ViewModel() {

    private val _protectionState = MutableStateFlow(ProtectionState())
    val protectionState: StateFlow<ProtectionState> = _protectionState.asStateFlow()

    private val _isPinSet = MutableStateFlow(false)
    val isPinSet: StateFlow<Boolean> = _isPinSet.asStateFlow()

    init {
        observeState()
        checkPinStatus()
    }

    private fun observeState() {
        viewModelScope.launch {
            combine(
                settingsRepository.isProtectionActive(),
                blockLogRepository.getTodayBlockCount(),
                blockLogRepository.getTotalBlockCount(),
                settingsRepository.isKeywordDetectionEnabled(),
                settingsRepository.isAiDetectionEnabled()
            ) { isActive, todayCount, totalCount, keywordEnabled, aiEnabled ->
                ProtectionState(
                    isActive = isActive,
                    blockedTodayCount = todayCount,
                    totalBlockedCount = totalCount,
                    isKeywordDetectionEnabled = keywordEnabled,
                    isAiDetectionEnabled = aiEnabled
                )
            }.collect { state ->
                _protectionState.value = state
            }
        }
    }

    private fun checkPinStatus() {
        viewModelScope.launch {
            _isPinSet.value = settingsRepository.isPinSet()
        }
    }

    fun toggleProtection(active: Boolean) {
        viewModelScope.launch {
            settingsRepository.setProtectionActive(active)
        }
    }

    fun getBlockedAppsCount(): Flow<Int> {
        return appRepository.getBlockedApps().map { it.size }
    }

    fun getWhitelistedAppsCount(): Flow<Int> {
        return appRepository.getWhitelistedApps().map { it.size }
    }
}