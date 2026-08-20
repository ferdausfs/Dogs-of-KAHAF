package com.guardian.shield.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY appName ASC")
    fun observeAll(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules")
    suspend fun getAll(): List<AppRuleEntity>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg LIMIT 1")
    suspend fun get(pkg: String): AppRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AppRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<AppRuleEntity>)

    @Query("DELETE FROM app_rules WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("SELECT packageName FROM app_rules WHERE isBlocked = 1")
    suspend fun blockedPackages(): List<String>

    @Query("SELECT packageName FROM app_rules WHERE isWhitelisted = 1")
    suspend fun whitelistPackages(): List<String>
}

@Dao
interface KeywordDao {
    @Query("SELECT * FROM keyword_rules ORDER BY id DESC")
    fun observeAll(): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE enabled = 1")
    suspend fun getEnabled(): List<KeywordRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: KeywordRuleEntity): Long

    @Query("DELETE FROM keyword_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM keyword_rules")
    suspend fun clear()
}

@Dao
interface BlockEventDao {
    @Query("SELECT * FROM block_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<BlockEventEntity>>

    @Query("SELECT * FROM block_events ORDER BY timestamp DESC")
    suspend fun getAll(): List<BlockEventEntity>

    // PHASE 2/3 (v3.5.0) — accountability weekly digest + clean-streak
    // computation. One bounded window query; bucketing happens in Kotlin.
    @Query("SELECT * FROM block_events WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun eventsSince(since: Long): List<BlockEventEntity>

    // PHASE 3 (v3.5.0) — reactive variant for the dashboard streak card.
    @Query("SELECT * FROM block_events WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun observeSince(since: Long): Flow<List<BlockEventEntity>>

    @Query("SELECT COUNT(*) FROM block_events WHERE timestamp >= :since")
    fun countSinceFlow(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_events WHERE timestamp >= :since AND reason = :reason")
    fun countByReasonFlow(since: Long, reason: String): Flow<Int>

    @Query("SELECT packageName FROM block_events WHERE timestamp >= :since GROUP BY packageName ORDER BY COUNT(*) DESC LIMIT 1")
    fun topPackageFlow(since: Long): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: BlockEventEntity): Long

    @Query("DELETE FROM block_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM block_events")
    suspend fun clear()
}

// TASK B — DAO for the confidence-based cooling-off queue.
@Dao
interface PendingReportDao {
    @Query("SELECT * FROM pending_reports WHERE status = 'PENDING' ORDER BY scheduledApplyAt ASC")
    fun observePending(): Flow<List<PendingReportEntity>>

    @Query("SELECT * FROM pending_reports WHERE status = 'PENDING' ORDER BY scheduledApplyAt ASC")
    suspend fun getPending(): List<PendingReportEntity>

    @Query("SELECT * FROM pending_reports WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingReportEntity?

    // Count HIGH-confidence reports for a given package within the trailing window
    // (used for escalating delay computation).
    @Query("SELECT COUNT(*) FROM pending_reports WHERE packageName = :pkg AND timestampCreated >= :since")
    suspend fun countHighConfSince(pkg: String, since: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: PendingReportEntity): Long

    @Query("UPDATE pending_reports SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE pending_reports SET status = 'CANCELLED' WHERE id = :id AND status = 'PENDING'")
    suspend fun cancel(id: Long)

    @Query("DELETE FROM pending_reports WHERE status != 'PENDING' AND timestampCreated < :before")
    suspend fun purgeOld(before: Long)
}

@Dao
interface ScheduleRuleDao {
    @Query("SELECT * FROM schedule_rules ORDER BY packageName ASC")
    fun observeAll(): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules")
    suspend fun getAll(): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rules WHERE packageName = :pkg LIMIT 1")
    suspend fun get(pkg: String): ScheduleRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ScheduleRuleEntity)

    @Query("DELETE FROM schedule_rules WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}
