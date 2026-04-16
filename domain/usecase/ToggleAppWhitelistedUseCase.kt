package com.kahaf.guardian.domain.usecase

import com.kahaf.guardian.domain.repository.AppRepository
import javax.inject.Inject

class ToggleAppWhitelistedUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String, appName: String, whitelisted: Boolean) {
        appRepository.setAppWhitelisted(packageName, appName, whitelisted)
        // If whitelisting, remove from blocked
        if (whitelisted) {
            appRepository.setAppBlocked(packageName, appName, false)
        }
    }
}