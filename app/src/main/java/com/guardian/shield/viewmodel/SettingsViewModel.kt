package com.guardian.shield.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.ModelImportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelSlotUi(
    val isImported: Boolean = false,
    val isImporting: Boolean = false,
    val readableSize: String? = null
)

data class SettingsUiState(
    val keywordFilter: Boolean = true,
    val aiDetection: Boolean = false,
    val delaySeconds: Int = 30,
    val aiThreshold: Float = 0.7f,
    val userGender: String = "NONE",
    val modelLoaded: Boolean = false,
    val genderModelAvailable: Boolean = false,
    val legacyModel: ModelSlotUi = ModelSlotUi(),
    val nsfwModel: ModelSlotUi = ModelSlotUi(),
    val genderModel: ModelSlotUi = ModelSlotUi()
)

sealed class SettingsEvent {
    data class ImportSuccess(val modelName: String) : SettingsEvent()
    data class ImportFailure(val message: String) : SettingsEvent()
    data class ModelDeleted(val modelName: String) : SettingsEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: GuardianPreferences,
    private val importer: ModelImportManager,
    private val aiDetector: AiDetector
) : ViewModel() {

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val refreshTick = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.keywordFilter,
        prefs.aiDetection,
        prefs.delaySeconds,
        prefs.aiThreshold,
        prefs.userGender,
        refreshTick
    ) { values ->
        val kw = values[0] as Boolean
        val ai = values[1] as Boolean
        val delay = values[2] as Int
        val th = values[3] as Float
        val gender = values[4] as String
        SettingsUiState(
            keywordFilter = kw,
            aiDetection = ai,
            delaySeconds = delay,
            aiThreshold = th,
            userGender = gender,
            modelLoaded = importer.isModelImported(AiDetector.MODEL_LEGACY),
            genderModelAvailable = importer.isModelImported(AiDetector.MODEL_GENDER),
            legacyModel = slot(AiDetector.MODEL_LEGACY),
            nsfwModel = slot(AiDetector.MODEL_NSFW),
            genderModel = slot(AiDetector.MODEL_GENDER)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private fun slot(name: String): ModelSlotUi {
        val imported = importer.isModelImported(name)
        val size = importer.modelSizeBytes(name)
        val readable = if (size > 0) formatBytes(size) else null
        return ModelSlotUi(isImported = imported, isImporting = false, readableSize = readable)
    }

    private fun formatBytes(b: Long): String {
        val mb = b / (1024.0 * 1024.0)
        return "%.1f MB".format(mb)
    }

    fun setKeywordFilter(v: Boolean) { viewModelScope.launch { prefs.setKeywordFilter(v) } }
    fun setAiDetection(v: Boolean) { viewModelScope.launch { prefs.setAiDetection(v) } }
    fun setDelaySeconds(v: Int) { viewModelScope.launch { prefs.setDelaySeconds(v) } }
    fun setAiThreshold(v: Float) { viewModelScope.launch { prefs.setAiThreshold(v) } }
    fun setUserGender(v: String) { viewModelScope.launch { prefs.setUserGender(v) } }

    fun importModel(uri: Uri, modelName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = importer.importModel(uri, modelName)
            refreshTick.value = refreshTick.value + 1
            if (r.isSuccess) _events.trySend(SettingsEvent.ImportSuccess(modelName))
            else _events.trySend(SettingsEvent.ImportFailure(r.exceptionOrNull()?.message ?: "Failed"))
        }
    }

    fun deleteModel(modelName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            importer.deleteModel(modelName)
            refreshTick.value = refreshTick.value + 1
            _events.trySend(SettingsEvent.ModelDeleted(modelName))
        }
    }
}
