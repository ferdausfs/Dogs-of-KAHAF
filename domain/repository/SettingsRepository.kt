package com.kahaf.guardian.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun isProtectionActive(): Flow<Boolean>
    suspend fun setProtectionActive(active: Boolean)
    fun isKeywordDetectionEnabled(): Flow<Boolean>
    suspend fun setKeywordDetectionEnabled(enabled: Boolean)
    fun isAiDetectionEnabled(): Flow<Boolean>
    suspend fun setAiDetectionEnabled(enabled: Boolean)
    fun isStrictModeEnabled(): Flow<Boolean>
    suspend fun setStrictModeEnabled(enabled: Boolean)
    fun getDelaySeconds(): Flow<Int>
    suspend fun setDelaySeconds(seconds: Int)
    suspend fun isPinSet(): Boolean
    suspend fun setPin(pin: String)
    suspend fun verifyPin(pin: String): Boolean
}