package com.kahaf.guardian.domain.usecase

import com.kahaf.guardian.domain.repository.AppRepository
import javax.inject.Inject

class ToggleAppWhitelistedUseCase @Inject constructor(private val repo: AppRepository) {
    suspend operator fun invoke(pkg: String, name: String, whitelisted: Boolean) {
        repo.setAppWhitelisted(pkg, name, whitelisted)
        if (whitelisted) repo.setAppBlocked(pkg, name, false)
    }
}
