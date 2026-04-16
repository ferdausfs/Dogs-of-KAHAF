package com.kahaf.guardian.engine.accountability

import com.kahaf.guardian.domain.model.BlockEvent
import com.kahaf.guardian.domain.repository.BlockLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountabilityEngine @Inject constructor(private val repo: BlockLogRepository) {
    fun getTodayBlockCount(): Flow<Int> = repo.getTodayBlockCount()
    fun getTotalBlockCount(): Flow<Int> = repo.getTotalBlockCount()
    fun getRecentActivity(limit: Int = 50): Flow<List<BlockEvent>> = repo.getRecentLogs(limit)
}
