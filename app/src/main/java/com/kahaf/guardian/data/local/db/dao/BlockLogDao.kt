package com.kahaf.guardian.data.local.db.dao

import androidx.room.*
import com.kahaf.guardian.data.local.db.entity.BlockLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockLogDao {
    @Insert
    suspend fun insert(log: BlockLogEntity)

    @Query("SELECT COUNT(*) FROM block_logs WHERE timestamp >= :startOfDay")
    fun getTodayCount(startOfDay: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_logs")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT * FROM block_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<BlockLogEntity>>

    @Query("DELETE FROM block_logs WHERE timestamp < :before")
    suspend fun deleteOldLogs(before: Long)
}
