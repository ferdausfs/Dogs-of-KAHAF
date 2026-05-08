package com.guardian.shield.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_NONE
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.ModelImportManager
import com.guardian.shield.service.detection.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Per-model UI state for the "AI Models" section in Settings.
 */
data class ModelSlotUi(
    val isImported: Boolean = false,
    /** "42.3 MB" — null when not imported. */
    val readableSize: String? = null,
    /** True while a copy is in progress; UI should disable buttons. */
    val isImporting: Boolean = false
)

data class SettingsUi(
    val keywordFilter: Boolean = true,
    val aiDetection: Boolean = false,
    val delaySeconds: Int = 30,
    val aiThreshold: Float = 0.7f,
    /** Legacy combined model — kept for back-compat with the old upload button. */
    val modelLoaded: Boolean = false,
    /** "MALE" / "FEMALE" / "NONE" */
    val userGender: String = GENDER_NONE,
    val genderModelAvailable: Boolean = false,

    // ── New per-model slots ──
    val nsfwModel: ModelSlotUi = ModelSlotUi(),
    val genderModel: ModelSlotUi = ModelSlotUi()
)

/**
 * One-shot events the Activity should react to (toasts, snackbars, etc.).
 */
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

    /** Per-model "import in progress" flags. */
    private val nsfwImporting   = MutableStateFlow(false)
    private val genderImporting = MutableStateFlow(false)

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    val ui: StateFlow<SettingsUi> = combine(
        prefs.keywordFilterEnabled,
        prefs.aiDetectionEnabled,
        prefs.delaySeconds,
        prefs.aiThreshold,
        prefs.userGender,
        refreshTrigger,
        nsfwImporting,
        genderImporting
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val keywordFilter = values[0] as Boolean
        @Suppress("UNCHECKED_CAST")
        val aiDetection   = values[1] as Boolean
        @Suppress("UNCHECKED_CAST")
        val delaySeconds  = values[2] as Int
        @Suppress("UNCHECKED_CAST")
        val aiThreshold   = values[3] as Float
        @Suppress("UNCHECKED_CAST")
        val userGender    = values[4] as String
        @Suppress("UNCHECKED_CAST")
        val nsfwBusy      = values[6] as Boolean
        @Suppress("UNCHECKED_CAST")
        val genderBusy    = values[7] as Boolean

        SettingsUi(
            keywordFilter        = keywordFilter,
            aiDetection          = aiDetection,
            delaySeconds         = delaySeconds,
            aiThreshold          = aiThreshold,
            modelLoaded          = ai.isModelAvailable() || ai.isNsfwModelAvailable(),
            userGender           = userGender,
            genderModelAvailable = ai.isGenderModelAvailable(),
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

    // ── Existing setters ──────────────────────────────────────────────
    fun setKeywordFilter(v: Boolean) = viewModelScope.launch { prefs.setKeywordFilter(v) }
    fun setAiDetection(v: Boolean)   = viewModelScope.launch { prefs.setAiDetection(v) }
    fun setDelaySeconds(v: Int)      = viewModelScope.launch { prefs.setDelaySeconds(v) }
    fun setAiThreshold(v: Float)     = viewModelScope.launch { prefs.setAiThreshold(v) }
    fun setUserGender(v: String)     = viewModelScope.launch { prefs.setUserGender(v) }
    fun resetPin()                   = pinManager.clearPin()
    fun refresh()                    { refreshTrigger.value = refreshTrigger.value + 1 }

    // ── Model import / reset ──────────────────────────────────────────

    /**
     * Copy the user-selected file into filesDir as [modelName].
     * Emits a [SettingsEvent.ImportSuccess] / [SettingsEvent.ImportFailure].
     */
    fun importModel(uri: Uri, modelName: String) {
        val busyFlag = busyFlagFor(modelName) ?: return
        viewModelScope.launch {
            busyFlag.value = true
            try {
                // The interpreter caches the previous model's bytes — force a clean
                // reload on next use so the freshly-imported file actually takes effect.
                runCatching { ai.close() }

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

    /** Delete an imported model so the user can re-import a fresh one. */
    fun resetModel(modelName: String) {
        viewModelScope.launch {
            // Drop the in-memory interpreter so it doesn't keep using the deleted file.
            runCatching { ai.close() }
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
