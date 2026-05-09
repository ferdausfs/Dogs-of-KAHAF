package com.kahaf.guardianshield.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kahaf.guardianshield.data.db.entity.BlockEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockEventDao {

    @Query("SELECT * FROM block_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<BlockEventEntity>>

    @Query("SELECT COUNT(*) FROM block_events WHERE timestamp >= :sinceEpochMs")
    fun observeCountSince(sinceEpochMs: Long): Flow<Int>

    @Insert
    suspend fun insert(event: BlockEventEntity): Long

    @Query("DELETE FROM block_events WHERE timestamp < :olderThanMs")
    suspend fun pruneOlderThan(olderThanMs: Long)

    @Query("DELETE FROM block_events")
    suspend fun deleteAll()
}
