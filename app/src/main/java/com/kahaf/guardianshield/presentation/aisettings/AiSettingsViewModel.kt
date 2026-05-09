package com.kahaf.guardianshield.presentation.aisettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardianshield.domain.model.AiSettings
import com.kahaf.guardianshield.domain.model.InstalledApp
import com.kahaf.guardianshield.domain.repository.AppRuleRepository
import com.kahaf.guardianshield.domain.repository.SettingsRepository
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
class AiSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appRuleRepository: AppRuleRepository
) : ViewModel() {

    val ai: StateFlow<AiSettings> = settingsRepository.aiSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AiSettings())

    private val _allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val allApps: StateFlow<List<InstalledApp>> = _allApps.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _allApps.value = appRuleRepository.getInstalledApps(includeSystem = false)
        }
    }

    fun setSensitivity(value: Float) = viewModelScope.launch {
        settingsRepository.updateAiSettings { it.copy(sensitivity = value) }
    }

    fun setDebounceFrames(frames: Int) = viewModelScope.launch {
        settingsRepository.updateAiSettings { it.copy(debounceFrames = frames.coerceIn(1, 10)) }
    }

    fun setDebounceWindow(ms: Long) = viewModelScope.launch {
        settingsRepository.updateAiSettings { it.copy(debounceWindowMs = ms.coerceIn(500L, 30_000L)) }
    }

    fun toggleSource(pkg: String, enabled: Boolean) = viewModelScope.launch {
        settingsRepository.updateAiSettings {
            val newSet = if (enabled) it.contentSourcePackages + pkg
            else it.contentSourcePackages - pkg
            it.copy(contentSourcePackages = newSet)
        }
    }

    fun setEngine(engine: String) = viewModelScope.launch {
        settingsRepository.updateAiSettings { it.copy(engine = engine) }
    }

    fun setPerAppBoost(pkg: String, boost: Float) = viewModelScope.launch {
        settingsRepository.updateAiSettings {
            val map = it.perAppBoost.toMutableMap()
            if (boost == 0f) map.remove(pkg) else map[pkg] = boost
            it.copy(perAppBoost = map)
        }
    }
}
