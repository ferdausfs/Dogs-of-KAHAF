package com.kahaf.guardianshield.domain.repository

import com.kahaf.guardianshield.domain.model.BlockEvent
import com.kahaf.guardianshield.domain.model.BlockReason
import kotlinx.coroutines.flow.Flow

interface BlockEventRepository {
    fun observeRecent(limit: Int = 50): Flow<List<BlockEvent>>
    fun observeBlocksTodayCount(): Flow<Int>
    suspend fun log(packageName: String, reason: BlockReason, detail: String = "")
    suspend fun pruneOlderThan(olderThanMs: Long)
    suspend fun clear()
}
