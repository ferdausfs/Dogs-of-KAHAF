package com.kahaf.guardian.engine.accountability

import com.kahaf.guardian.domain.model.BlockEvent
import com.kahaf.guardian.domain.repository.BlockLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accountability Engine - Tracks and reports blocking activity.
 * Extensible for future features like friend approval system.
 */
@Singleton
class AccountabilityEngine @Inject constructor(
    private val blockLogRepository: BlockLogRepository
) {
    fun getTodayBlockCount(): Flow<Int> = blockLogRepository.getTodayBlockCount()

    fun getTotalBlockCount(): Flow<Int> = blockLogRepository.getTotalBlockCount()

    fun getRecentActivity(limit: Int = 50): Flow<List<BlockEvent>> =
        blockLogRepository.getRecentLogs(limit)
}