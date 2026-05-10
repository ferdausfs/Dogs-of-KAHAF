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

    /** Aggregated counts per `reason` since a given epoch ms. */
    @Query(
        """
        SELECT reason AS reason, COUNT(*) AS count
        FROM block_events
        WHERE timestamp >= :sinceEpochMs
        GROUP BY reason
        """
    )
    fun observeByReasonSince(sinceEpochMs: Long): Flow<List<ReasonCount>>

    @Query(
        """
        SELECT packageName AS packageName, COUNT(*) AS count
        FROM block_events
        WHERE timestamp >= :sinceEpochMs
        GROUP BY packageName
        ORDER BY count DESC
        LIMIT :limit
        """
    )
    fun observeTopPackagesSince(sinceEpochMs: Long, limit: Int): Flow<List<PackageCount>>

    @Insert
    suspend fun insert(event: BlockEventEntity): Long

    @Query("DELETE FROM block_events WHERE timestamp < :olderThanMs")
    suspend fun pruneOlderThan(olderThanMs: Long)

    @Query("DELETE FROM block_events")
    suspend fun deleteAll()
}

data class ReasonCount(val reason: String, val count: Int)
data class PackageCount(val packageName: String, val count: Int)
