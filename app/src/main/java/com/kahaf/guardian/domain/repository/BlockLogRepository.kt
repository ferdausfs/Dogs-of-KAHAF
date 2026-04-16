package com.kahaf.guardian.domain.repository

import com.kahaf.guardian.domain.model.BlockEvent
import kotlinx.coroutines.flow.Flow

interface BlockLogRepository {
    suspend fun logBlockEvent(event: BlockEvent)
    fun getTodayBlockCount(): Flow<Int>
    fun getTotalBlockCount(): Flow<Int>
    fun getRecentLogs(limit: Int = 50): Flow<List<BlockEvent>>
}
