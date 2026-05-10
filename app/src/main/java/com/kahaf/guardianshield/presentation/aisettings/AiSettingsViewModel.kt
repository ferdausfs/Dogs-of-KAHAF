package com.kahaf.guardianshield.presentation.aisettings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kahaf.guardianshield.data.classifier.ModelImportManager
import com.kahaf.guardianshield.data.classifier.TfLiteNsfwClassifier
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

/**
 * v3.1.0 (legacy merge):
 *  - injected [ModelImportManager] so the user can import a custom .tflite
 *    NSFW model from Storage Access Framework. Result is surfaced via
 *    [importStatus] (success message or human-readable error).
 *  - exposed convenience setters for the LOW/BALANCED/HIGH presets ported
 *    from the legacy GuardianConstants.
 *
 * v3.0.0:
 *  - removed `setEngine` (engine toggle is gone — TFLite is the only engine).
 *  - added `setMinImageSize`, `setModelInputNormalized`, `setHeuristicEnabled`.
 *  - exposes `isModelLoaded` from the classifier so the UI can show
 *    "Loaded" vs. "Not found — using safe fallback".
 */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appRuleRepository: AppRuleRepository,
    private val classifier: TfLiteNsfwClassifier,
    private val modelImportManager: ModelImportManager
) : ViewModel() {

    val ai: StateFlow<AiSettings> = settingsRepository.aiSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AiSettings())

    /** True after the TFLite model is mapped + warmed up. */
    val isModelLoaded: StateFlow<Boolean> = classifier.isModelLoaded
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val allApps: StateFlow<List<InstalledApp>> = _allApps.asStateFlow()

    /** Custom model import status: null = idle, "" = loading, otherwise message. */
    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    /** Reflects whether a user-imported nsfw_model.tflite is present in filesDir. */
    private val _customModelImported = MutableStateFlow(
        modelImportManager.isImported(ModelImportManager.NSFW_MODEL_FILE)
    )
    val customModelImported: StateFlow<Boolean> = _customModelImported.asStateFlow()

    private val _customModelSize = MutableStateFlow(
        modelImportManager.getModelSize(ModelImportManager.NSFW_MODEL_FILE)
    )
    val customModelSize: StateFlow<String?> = _customModelSize.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _allApps.value = appRuleRepository.getInstalledApps(includeSystem = false)
        }
    }

    fun setSensitivity(value: Float) = viewModelScope.launch {
        settingsRepository.updateAiSettings { it.copy(sensitivity = value) }
    }

    /** v3.1.0: LOW/BALANCED/HIGH preset → maps to a sensitivity slider value. */
    fun applyPreset(preset: String) = viewModelScope.launch {
        val sensitivity = when (preset) {
            "LOW"  -> 0.30f   // strict threshold => fewer false positives
            "HIGH" -> 0.75f   // looser threshold => catches more
            else   -> 0.55f   // BALANCED
        }
        settingsRepository.updateAiSettings { it.copy(sensitivity = sensitivity) }
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

    fun setPerAppBoost(pkg: String, boost: Float) = viewModelScope.launch {
        settingsRepository.updateAiSettings {
            val map = it.perAppBoost.toMutableMap()
            if (boost == 0f) map.remove(pkg) else map[pkg] = boost
            it.copy(perAppBoost = map)
        }
    }

    fun setMinImageSize(px: Int) = viewModelScope.launch {
        settingsRepository.updateAiSettings { it.copy(minImageSize = px.coerceIn(50, 500)) }
    }

    fun setModelInputNormalized(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.updateAiSettings { it.copy(modelInputNormalized = enabled) }
    }

    fun setHeuristicEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.updateAiSettings { it.copy(heuristicEnabled = enabled) }
    }

    // ── v3.1.0 (legacy merge): custom NSFW model import / delete ────────────
    fun importCustomNsfwModel(uri: Uri) = viewModelScope.launch {
        _importStatus.value = ""    // sentinel: in-flight
        val result = modelImportManager.importModel(uri, ModelImportManager.NSFW_MODEL_FILE)
        result.onSuccess {
            _importStatus.value = "Custom model imported successfully"
            _customModelImported.value = true
            _customModelSize.value = modelImportManager.getModelSize(ModelImportManager.NSFW_MODEL_FILE)
        }.onFailure { t ->
            _importStatus.value = "Could not import model: ${t.message ?: "unknown error"}"
        }
    }

    fun deleteCustomNsfwModel() = viewModelScope.launch {
        modelImportManager.deleteModel(ModelImportManager.NSFW_MODEL_FILE)
        _customModelImported.value = false
        _customModelSize.value = null
    }

    fun clearImportStatus() { _importStatus.value = null }
}
