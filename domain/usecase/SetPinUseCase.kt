package com.kahaf.guardian.domain.usecase

import com.kahaf.guardian.domain.repository.SettingsRepository
import com.kahaf.guardian.util.Constants
import javax.inject.Inject

class SetPinUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(pin: String): Result<Unit> {
        if (pin.length < Constants.PIN_MIN_LENGTH) {
            return Result.failure(IllegalArgumentException("PIN must be at least ${Constants.PIN_MIN_LENGTH} digits"))
        }
        if (pin.length > Constants.PIN_MAX_LENGTH) {
            return Result.failure(IllegalArgumentException("PIN must be at most ${Constants.PIN_MAX_LENGTH} digits"))
        }
        if (!pin.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("PIN must contain only digits"))
        }
        settingsRepository.setPin(pin)
        return Result.success(Unit)
    }
}