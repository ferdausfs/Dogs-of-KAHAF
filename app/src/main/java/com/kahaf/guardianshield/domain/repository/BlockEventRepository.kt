package com.kahaf.guardianshield.domain.repository

import com.kahaf.guardianshield.domain.model.BlockEvent
import com.kahaf.guardianshield.domain.model.BlockReason
import kotlinx.coroutines.flow.Flow

interface BlockEventRepository {
    fun observeRecent(limit: Int = 50): Flow<List<BlockEvent>>
    fun observeBlocksTodayCount(): Flow<Int>

    /** v3.0.0: aggregated count of blocks per [BlockReason] since [sinceMs]. */
    fun getBlocksByReason(sinceMs: Long): Flow<Map<BlockReason, Int>>

    /** v3.0.0: top blocked package names since [sinceMs], capped at [limit]. */
    fun getTopBlockedApps(sinceMs: Long, limit: Int): Flow<List<Pair<String, Int>>>

    suspend fun log(packageName: String, reason: BlockReason, detail: String = "")
    suspend fun pruneOlderThan(olderThanMs: Long)
    suspend fun clear()
}
