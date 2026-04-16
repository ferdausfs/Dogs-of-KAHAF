package com.kahaf.guardian.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardian.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val keywordDetectionEnabled: Boolean = true,
    val aiDetectionEnabled: Boolean = false,
    val strictModeEnabled: Boolean = false,
    val delaySeconds: Int = 30
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _saveSuccess = MutableSharedFlow<Boolean>()
    val saveSuccess: SharedFlow<Boolean> = _saveSuccess.asSharedFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.isKeywordDetectionEnabled(),
                settingsRepository.isAiDetectionEnabled(),
                settingsRepository.isStrictModeEnabled(),
                settingsRepository.getDelaySeconds()
            ) { keyword, ai, strict, delay ->
                SettingsUiState(
                    keywordDetectionEnabled = keyword,
                    aiDetectionEnabled = ai,
                    strictModeEnabled = strict,
                    delaySeconds = delay
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun saveSettings(
        keywordEnabled: Boolean,
        aiEnabled: Boolean,
        strictEnabled: Boolean,
        delaySeconds: Int
    ) {
        viewModelScope.launch {
            try {
                settingsRepository.setKeywordDetectionEnabled(keywordEnabled)
                settingsRepository.setAiDetectionEnabled(aiEnabled)
                settingsRepository.setStrictModeEnabled(strictEnabled)
                settingsRepository.setDelaySeconds(delaySeconds)
                _saveSuccess.emit(true)
            } catch (e: Exception) {
                _saveSuccess.emit(false)
            }
        }
    }
}