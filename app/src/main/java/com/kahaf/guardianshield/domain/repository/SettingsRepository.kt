package com.kahaf.guardianshield.domain.repository

import com.kahaf.guardianshield.domain.model.AiSettings
import com.kahaf.guardianshield.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val appSettings: Flow<AppSettings>
    val aiSettings: Flow<AiSettings>

    suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings)
    suspend fun updateAiSettings(transform: (AiSettings) -> AiSettings)

    /** Returns a JSON snapshot of the full configuration (for export). */
    suspend fun exportJson(): String

    /** Restores configuration from a JSON snapshot (for import). */
    suspend fun importJson(json: String): Boolean
}
