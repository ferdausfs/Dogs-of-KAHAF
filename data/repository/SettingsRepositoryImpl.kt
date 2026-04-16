package com.kahaf.guardian.data.repository

import com.kahaf.guardian.data.local.prefs.SecurePrefsManager
import com.kahaf.guardian.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val securePrefs: SecurePrefsManager
) : SettingsRepository {

    private val _protectionActive = MutableStateFlow(securePrefs.isProtectionActive())
    private val _keywordDetection = MutableStateFlow(securePrefs.isKeywordDetectionEnabled())
    private val _aiDetection = MutableStateFlow(securePrefs.isAiDetectionEnabled())
    private val _strictMode = MutableStateFlow(securePrefs.isStrictModeEnabled())
    private val _delaySeconds = MutableStateFlow(securePrefs.getDelaySeconds())

    override fun isProtectionActive(): Flow<Boolean> = _protectionActive.asStateFlow()

    override suspend fun setProtectionActive(active: Boolean) {
        securePrefs.setProtectionActive(active)
        _protectionActive.value = active
    }

    override fun isKeywordDetectionEnabled(): Flow<Boolean> = _keywordDetection.asStateFlow()

    override suspend fun setKeywordDetectionEnabled(enabled: Boolean) {
        securePrefs.setKeywordDetectionEnabled(enabled)
        _keywordDetection.value = enabled
    }

    override fun isAiDetectionEnabled(): Flow<Boolean> = _aiDetection.asStateFlow()

    override suspend fun setAiDetectionEnabled(enabled: Boolean) {
        securePrefs.setAiDetectionEnabled(enabled)
        _aiDetection.value = enabled
    }

    override fun isStrictModeEnabled(): Flow<Boolean> = _strictMode.asStateFlow()

    override suspend fun setStrictModeEnabled(enabled: Boolean) {
        securePrefs.setStrictModeEnabled(enabled)
        _strictMode.value = enabled
    }

    override fun getDelaySeconds(): Flow<Int> = _delaySeconds.asStateFlow()

    override suspend fun setDelaySeconds(seconds: Int) {
        securePrefs.setDelaySeconds(seconds)
        _delaySeconds.value = seconds
    }

    override suspend fun isPinSet(): Boolean = securePrefs.isPinSet()

    override suspend fun setPin(pin: String) = securePrefs.setPin(pin)

    override suspend fun verifyPin(pin: String): Boolean = securePrefs.verifyPin(pin)
}