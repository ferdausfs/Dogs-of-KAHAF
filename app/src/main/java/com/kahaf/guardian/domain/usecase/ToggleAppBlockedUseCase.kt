package com.kahaf.guardian.domain.usecase

import com.kahaf.guardian.domain.repository.AppRepository
import javax.inject.Inject

class ToggleAppBlockedUseCase @Inject constructor(private val repo: AppRepository) {
    suspend operator fun invoke(pkg: String, name: String, blocked: Boolean) {
        repo.setAppBlocked(pkg, name, blocked)
        if (blocked) repo.setAppWhitelisted(pkg, name, false)
    }
}
