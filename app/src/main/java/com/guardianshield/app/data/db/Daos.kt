package com.guardianshield.app.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.guardianshield.app.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY appLabel COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg LIMIT 1")
    suspend fun get(pkg: String): AppRule?

    @Query("SELECT * FROM app_rules WHERE isBlocked = 1")
    suspend fun getAllBlocked(): List<AppRule>

    @Query("SELECT * FROM app_rules WHERE isWhitelisted = 1")
    suspend fun getAllWhitelisted(): List<AppRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AppRule)

    @Delete
    suspend fun delete(rule: AppRule)
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC LIMIT 500")
    fun observeRecent(): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_log WHERE eventType = :type ORDER BY timestamp DESC LIMIT 500")
    fun observeByType(type: String): Flow<List<ActivityLog>>

    @Insert
    suspend fun insert(log: ActivityLog)

    @Query("DELETE FROM activity_log WHERE timestamp < :cutoff")
    suspend fun pruneBefore(cutoff: Long)
}

@Dao
interface KeywordDao {
    @Query("SELECT * FROM keywords ORDER BY id DESC")
    fun observeAll(): Flow<List<KeywordFilter>>

    @Query("SELECT keyword FROM keywords WHERE enabled = 1")
    suspend fun getEnabledKeywords(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(k: KeywordFilter)

    @Delete
    suspend fun delete(k: KeywordFilter)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY startMinute ASC")
    fun observeAll(): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE enabled = 1")
    suspend fun getEnabled(): List<Schedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: Schedule)

    @Delete
    suspend fun delete(s: Schedule)
}
