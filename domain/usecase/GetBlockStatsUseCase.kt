package com.kahaf.guardian.domain.usecase

import com.kahaf.guardian.domain.repository.BlockLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBlockStatsUseCase @Inject constructor(
    private val blockLogRepository: BlockLogRepository
) {
    fun getTodayCount(): Flow<Int> = blockLogRepository.getTodayBlockCount()
    fun getTotalCount(): Flow<Int> = blockLogRepository.getTotalBlockCount()
}