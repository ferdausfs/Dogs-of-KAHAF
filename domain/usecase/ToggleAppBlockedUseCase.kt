package com.kahaf.guardian.domain.usecase

import com.kahaf.guardian.domain.repository.AppRepository
import javax.inject.Inject

class ToggleAppBlockedUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String, appName: String, blocked: Boolean) {
        appRepository.setAppBlocked(packageName, appName, blocked)
        // If blocking, remove from whitelist
        if (blocked) {
            appRepository.setAppWhitelisted(packageName, appName, false)
        }
    }
}