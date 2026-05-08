package com.guardian.shield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_NONE
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUi(
    val keywordFilter: Boolean = true,
    val aiDetection: Boolean = false,
    val delaySeconds: Int = 30,
    val aiThreshold: Float = 0.7f,
    val modelLoaded: Boolean = false,
    /** "MALE" / "FEMALE" / "NONE" */
    val userGender: String = GENDER_NONE,
    val genderModelAvailable: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: GuardianPreferences,
    private val ai: AiDetector,
    private val pinManager: PinManager
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)

    val ui: StateFlow<SettingsUi> = combine(
        prefs.keywordFilterEnabled,
        prefs.aiDetectionEnabled,
        prefs.delaySeconds,
        prefs.aiThreshold,
        prefs.userGender,
        refreshTrigger
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SettingsUi(
            keywordFilter        = values[0] as Boolean,
            aiDetection          = values[1] as Boolean,
            delaySeconds         = values[2] as Int,
            aiThreshold          = values[3] as Float,
            modelLoaded          = ai.isModelAvailable() || ai.isNsfwModelAvailable(),
            userGender           = values[4] as String,
            genderModelAvailable = ai.isGenderModelAvailable()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    fun setKeywordFilter(v: Boolean) = viewModelScope.launch { prefs.setKeywordFilter(v) }
    fun setAiDetection(v: Boolean)   = viewModelScope.launch { prefs.setAiDetection(v) }
    fun setDelaySeconds(v: Int)      = viewModelScope.launch { prefs.setDelaySeconds(v) }
    fun setAiThreshold(v: Float)     = viewModelScope.launch { prefs.setAiThreshold(v) }
    fun setUserGender(v: String)     = viewModelScope.launch { prefs.setUserGender(v) }
    fun resetPin()                   = pinManager.clearPin()
    fun refresh()                    { refreshTrigger.value = refreshTrigger.value + 1 }
}
