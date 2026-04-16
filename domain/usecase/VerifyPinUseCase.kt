package com.kahaf.guardian.domain.usecase

import com.kahaf.guardian.domain.repository.SettingsRepository
import javax.inject.Inject

class VerifyPinUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(pin: String): Boolean {
        return settingsRepository.verifyPin(pin)
    }
}