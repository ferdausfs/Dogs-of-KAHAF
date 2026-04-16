package com.kahaf.guardian.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardian.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(val keywordEnabled: Boolean = true, val aiEnabled: Boolean = false, val strictEnabled: Boolean = false, val delaySeconds: Int = 30)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState()); val uiState: StateFlow<SettingsUiState> = _state.asStateFlow()
    private val _saved = MutableSharedFlow<Boolean>(); val saveSuccess: SharedFlow<Boolean> = _saved.asSharedFlow()

    init { viewModelScope.launch { combine(repo.isKeywordDetectionEnabled(), repo.isAiDetectionEnabled(), repo.isStrictModeEnabled(), repo.getDelaySeconds()) { k, a, s, d -> SettingsUiState(k, a, s, d) }.collect { _state.value = it } } }

    fun save(keyword: Boolean, ai: Boolean, strict: Boolean, delay: Int) {
        viewModelScope.launch { try { repo.setKeywordDetectionEnabled(keyword); repo.setAiDetectionEnabled(ai); repo.setStrictModeEnabled(strict); repo.setDelaySeconds(delay); _saved.emit(true) } catch (_: Exception) { _saved.emit(false) } }
    }
}
