package com.kahaf.guardian.domain.usecase

import com.kahaf.guardian.domain.repository.AppRepository
import javax.inject.Inject

class CheckWhitelistUseCase @Inject constructor(private val repo: AppRepository) {
    suspend operator fun invoke(pkg: String): Boolean = repo.isAppWhitelisted(pkg)
}
