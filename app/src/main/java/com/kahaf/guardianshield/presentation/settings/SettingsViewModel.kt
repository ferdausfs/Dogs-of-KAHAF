package com.kahaf.guardianshield.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardianshield.domain.model.AppSettings
import com.kahaf.guardianshield.domain.model.ThemeMode
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import com.kahaf.guardianshield.domain.usecase.ExportImportConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val exportImport: ExportImportConfigUseCase
) : ViewModel() {

    val app: StateFlow<AppSettings> = settingsRepository.appSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _exportedJson = MutableStateFlow<String?>(null)
    val exportedJson: StateFlow<String?> = _exportedJson.asStateFlow()

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch {
        settingsRepository.updateAppSettings { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(on: Boolean) = viewModelScope.launch {
        settingsRepository.updateAppSettings { it.copy(dynamicColor = on) }
    }

    fun setUninstallProtection(on: Boolean) = viewModelScope.launch {
        settingsRepository.updateAppSettings { it.copy(uninstallProtection = on) }
    }

    fun export() = viewModelScope.launch {
        _exportedJson.value = exportImport.export()
    }

    fun import(json: String) = viewModelScope.launch {
        val ok = exportImport.import(json)
        _importMessage.value = if (ok) "Import OK" else "Import failed"
    }

    fun clearExport() { _exportedJson.value = null }
    fun clearImportMessage() { _importMessage.value = null }
}
