package com.kahaf.guardian.domain.usecase

import com.kahaf.guardian.domain.repository.AppRepository
import javax.inject.Inject

class CheckWhitelistUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    suspend operator fun invoke(packageName: String): Boolean {
        return appRepository.isAppWhitelisted(packageName)
    }
}