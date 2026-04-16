package com.kahaf.guardian.domain.usecase

import com.kahaf.guardian.domain.repository.BlockLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBlockStatsUseCase @Inject constructor(private val repo: BlockLogRepository) {
    fun getTodayCount(): Flow<Int> = repo.getTodayBlockCount()
    fun getTotalCount(): Flow<Int> = repo.getTotalBlockCount()
}
