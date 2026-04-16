package com.kahaf.guardian.data.repository

import com.kahaf.guardian.data.local.prefs.SecurePrefsManager
import com.kahaf.guardian.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(private val prefs: SecurePrefsManager) : SettingsRepository {
    private val _protection = MutableStateFlow(prefs.isProtectionActive())
    private val _keyword = MutableStateFlow(prefs.isKeywordDetectionEnabled())
    private val _ai = MutableStateFlow(prefs.isAiDetectionEnabled())
    private val _strict = MutableStateFlow(prefs.isStrictModeEnabled())
    private val _delay = MutableStateFlow(prefs.getDelaySeconds())

    override fun isProtectionActive(): Flow<Boolean> = _protection.asStateFlow()
    override suspend fun setProtectionActive(active: Boolean) { prefs.setProtectionActive(active); _protection.value = active }
    override fun isKeywordDetectionEnabled(): Flow<Boolean> = _keyword.asStateFlow()
    override suspend fun setKeywordDetectionEnabled(enabled: Boolean) { prefs.setKeywordDetectionEnabled(enabled); _keyword.value = enabled }
    override fun isAiDetectionEnabled(): Flow<Boolean> = _ai.asStateFlow()
    override suspend fun setAiDetectionEnabled(enabled: Boolean) { prefs.setAiDetectionEnabled(enabled); _ai.value = enabled }
    override fun isStrictModeEnabled(): Flow<Boolean> = _strict.asStateFlow()
    override suspend fun setStrictModeEnabled(enabled: Boolean) { prefs.setStrictModeEnabled(enabled); _strict.value = enabled }
    override fun getDelaySeconds(): Flow<Int> = _delay.asStateFlow()
    override suspend fun setDelaySeconds(seconds: Int) { prefs.setDelaySeconds(seconds); _delay.value = seconds }
    override suspend fun isPinSet() = prefs.isPinSet()
    override suspend fun setPin(pin: String) = prefs.setPin(pin)
    override suspend fun verifyPin(pin: String) = prefs.verifyPin(pin)
}
