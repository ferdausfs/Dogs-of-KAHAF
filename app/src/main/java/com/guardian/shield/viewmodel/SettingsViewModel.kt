package com.guardian.shield.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_NONE
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.ModelImportManager
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.util.GuardianConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * v11 (2.1.1) STABILITY PATCH:
 *  • CRITICAL FIX: importModel() / resetModel() previously called
 *    `ai.close()` which used runBlocking. From the Main dispatcher this
 *    blocked the UI thread and triggered the visible "App keeps stopping"
 *    crash on import. Now we call ai.closeSuspend() inside
 *    withContext(Dispatchers.IO) — main thread is never blocked.
 */
data class ModelSlotUi(
    val isImported: Boolean = false,
    val readableSize: String? = null,
    val isImporting: Boolean = false
)

data class SettingsUi(
    val keywordFilter: Boolean = true,
    val aiDetection: Boolean = false,
    val delaySeconds: Int = 30,
    val aiThreshold: Float = GuardianConstants.DEFAULT_AI_THRESHOLD,
    val modelLoaded: Boolean = false,
    val userGender: String = GENDER_NONE,
    val genderModelAvailable: Boolean = false,
    val sensitivity: String = GuardianConstants.SENSITIVITY_BALANCED,

    val nsfwModel: ModelSlotUi = ModelSlotUi(),
    val genderModel: ModelSlotUi = ModelSlotUi()
)

sealed class SettingsEvent {
    data class ImportSuccess(val modelName: String) : SettingsEvent()
    data class ImportFailure(val modelName: String, val message: String) : SettingsEvent()
    data class ModelDeleted(val modelName: String) : SettingsEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: GuardianPreferences,
    private val ai: AiDetector,
    private val pinManager: PinManager,
    private val modelImporter: ModelImportManager
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)

    private val nsfwImporting   = MutableStateFlow(false)
    private val genderImporting = MutableStateFlow(false)

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    val ui: StateFlow<SettingsUi> = combine(
        listOf(
            prefs.keywordFilterEnabled,
            prefs.aiDetectionEnabled,
            prefs.delaySeconds,
            prefs.aiThreshold,
            prefs.userGender,
            prefs.sensitivity,
            refreshTrigger,
            nsfwImporting,
            genderImporting
        )
    ) { values ->
        @Suppress("UNCHECKED_CAST") val keywordFilter = values[0] as Boolean
        @Suppress("UNCHECKED_CAST") val aiDetection   = values[1] as Boolean
        @Suppress("UNCHECKED_CAST") val delaySeconds  = values[2] as Int
        @Suppress("UNCHECKED_CAST") val aiThreshold   = values[3] as Float
        @Suppress("UNCHECKED_CAST") val userGender    = values[4] as String
        @Suppress("UNCHECKED_CAST") val sensitivity   = values[5] as String
        @Suppress("UNCHECKED_CAST") val nsfwBusy      = values[7] as Boolean
        @Suppress("UNCHECKED_CAST") val genderBusy    = values[8] as Boolean

        SettingsUi(
            keywordFilter        = keywordFilter,
            aiDetection          = aiDetection,
            delaySeconds         = delaySeconds,
            aiThreshold          = aiThreshold,
            modelLoaded          = ai.isModelAvailable() || ai.isNsfwModelAvailable(),
            userGender           = userGender,
            genderModelAvailable = ai.isGenderModelAvailable(),
            sensitivity          = sensitivity,
            nsfwModel = ModelSlotUi(
                isImported   = modelImporter.isImported(AiDetector.NSFW_MODEL_FILE),
                readableSize = modelImporter.getModelSize(AiDetector.NSFW_MODEL_FILE),
                isImporting  = nsfwBusy
            ),
            genderModel = ModelSlotUi(
                isImported   = modelImporter.isImported(AiDetector.GENDER_MODEL_FILE),
                readableSize = modelImporter.getModelSize(AiDetector.GENDER_MODEL_FILE),
                isImporting  = genderBusy
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    fun setKeywordFilter(v: Boolean) = viewModelScope.launch { prefs.setKeywordFilter(v) }
    fun setAiDetection(v: Boolean)   = viewModelScope.launch { prefs.setAiDetection(v) }
    fun setDelaySeconds(v: Int)      = viewModelScope.launch { prefs.setDelaySeconds(v) }
    fun setAiThreshold(v: Float)     = viewModelScope.launch { prefs.setAiThreshold(v) }
    fun setUserGender(v: String)     = viewModelScope.launch { prefs.setUserGender(v) }

    fun setSensitivity(level: String) = viewModelScope.launch {
        prefs.setSensitivity(level)
        prefs.setAiThreshold(GuardianConstants.thresholdForSensitivity(level))
    }

    fun resetPin()                   = pinManager.clearPin()
    fun refresh()                    { refreshTrigger.value = refreshTrigger.value + 1 }

    fun importModel(uri: Uri, modelName: String) {
        val busyFlag = busyFlagFor(modelName) ?: return
        viewModelScope.launch {
            busyFlag.value = true
            try {
                // v11 FIX: was `ai.close()` (runBlocking on Main → ANR).
                // Now: suspend version on IO dispatcher.
                withContext(Dispatchers.IO) {
                    runCatching { ai.closeSuspend() }
                }
                val result = modelImporter.importModel(uri, modelName)
                refresh()
                if (result.isSuccess) {
                    _events.emit(SettingsEvent.ImportSuccess(modelName))
                } else {
                    _events.emit(
                        SettingsEvent.ImportFailure(
                            modelName = modelName,
                            message = result.exceptionOrNull()?.message ?: "Unknown error"
                        )
                    )
                }
            } finally {
                busyFlag.value = false
            }
        }
    }

    fun resetModel(modelName: String) {
        viewModelScope.launch {
            // v11 FIX — same root cause as importModel above.
            withContext(Dispatchers.IO) {
                runCatching { ai.closeSuspend() }
            }
            val removed = modelImporter.deleteModel(modelName)
            refresh()
            if (removed) _events.emit(SettingsEvent.ModelDeleted(modelName))
        }
    }

    private fun busyFlagFor(modelName: String): MutableStateFlow<Boolean>? = when (modelName) {
        AiDetector.NSFW_MODEL_FILE   -> nsfwImporting
        AiDetector.GENDER_MODEL_FILE -> genderImporting
        else -> null
    }
}
