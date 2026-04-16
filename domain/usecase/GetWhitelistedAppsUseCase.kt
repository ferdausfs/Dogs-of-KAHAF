package com.kahaf.guardian.domain.usecase

import com.kahaf.guardian.domain.model.AppInfo
import com.kahaf.guardian.domain.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWhitelistedAppsUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    operator fun invoke(): Flow<List<AppInfo>> = appRepository.getWhitelistedApps()
}