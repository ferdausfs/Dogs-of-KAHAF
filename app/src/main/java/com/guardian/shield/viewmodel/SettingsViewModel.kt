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
    val userGender: String = "NONE",
    val tempBlockDurationMins: Int = 15,
    // ✅ সব threshold
    val aiThreshold: Float = 0.65f,
    val nsfwGateThreshold: Float = 0.60f,
    val genderThreshold: Float = 0.70f,
    val gridVoteCount: Int = 2,
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
        prefs.userGender,
        prefs.tempBlockDurationMins,
        prefs.aiThreshold,
        prefs.nsfwGateThreshold,
        prefs.genderThreshold,
        prefs.gridVoteCount,
        refreshTick
    ) { values ->
        SettingsUiState(
            keywordFilter = values[0] as Boolean,
            aiDetection = values[1] as Boolean,
            delaySeconds = values[2] as Int,
            userGender = values[3] as String,
            tempBlockDurationMins = values[4] as Int,
            aiThreshold = values[5] as Float,
            nsfwGateThreshold = values[6] as Float,
            genderThreshold = values[7] as Float,
            gridVoteCount = values[8] as Int,
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
        return ModelSlotUi(
            isImported = imported,
            readableSize = if (size > 0) "%.1f MB".format(size / 1_048_576.0) else null
        )
    }

    fun setKeywordFilter(v: Boolean) { viewModelScope.launch { prefs.setKeywordFilter(v); prefs.bumpRulesVersion() } }
    fun setAiDetection(v: Boolean) { viewModelScope.launch { prefs.setAiDetection(v); prefs.bumpRulesVersion() } }
    fun setDelaySeconds(v: Int) { viewModelScope.launch { prefs.setDelaySeconds(v) } }
    fun setUserGender(v: String) { viewModelScope.launch { prefs.setUserGender(v); prefs.bumpRulesVersion() } }
    fun setTempBlockDurationMins(v: Int) { viewModelScope.launch { prefs.setTempBlockDurationMins(v) } }

    // ✅ নতুন setters
    fun setAiThreshold(v: Float) { viewModelScope.launch { prefs.setAiThreshold(v) } }
    fun setNsfwGateThreshold(v: Float) { viewModelScope.launch { prefs.setNsfwGateThreshold(v) } }
    fun setGenderThreshold(v: Float) { viewModelScope.launch { prefs.setGenderThreshold(v) } }
    fun setGridVoteCount(v: Int) { viewModelScope.launch { prefs.setGridVoteCount(v) } }

    fun importModel(uri: Uri, modelName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = importer.importModel(uri, modelName)
            refreshTick.value++
            prefs.bumpRulesVersion()
            if (r.isSuccess) _events.trySend(SettingsEvent.ImportSuccess(modelName))
            else _events.trySend(SettingsEvent.ImportFailure(r.exceptionOrNull()?.message ?: "Failed"))
        }
    }

    fun deleteModel(modelName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            importer.deleteModel(modelName)
            refreshTick.value++
            prefs.bumpRulesVersion()
            _events.trySend(SettingsEvent.ModelDeleted(modelName))
        }
    }
}