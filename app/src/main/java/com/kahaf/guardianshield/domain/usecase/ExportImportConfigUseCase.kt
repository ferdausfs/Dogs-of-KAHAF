package com.kahaf.guardianshield.domain.usecase

import com.kahaf.guardianshield.domain.repository.SettingsRepository
import javax.inject.Inject

class ExportImportConfigUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend fun export(): String = settingsRepository.exportJson()
    suspend fun import(json: String): Boolean = settingsRepository.importJson(json)
}
