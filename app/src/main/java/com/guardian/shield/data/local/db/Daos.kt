package com.guardian.shield.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY appName ASC")
    fun observeAll(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules")
    suspend fun getAll(): List<AppRuleEntity>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): AppRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppRuleEntity)

    @Query("DELETE FROM app_rules WHERE packageName = :pkg")
    suspend fun deleteByPackage(pkg: String)
}

@Dao
interface KeywordDao {
    @Query("SELECT * FROM keyword_rules ORDER BY id DESC")
    fun observeAll(): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE enabled = 1")
    suspend fun getAllEnabled(): List<KeywordRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KeywordRuleEntity)

    @Query("DELETE FROM keyword_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BlockEventDao {
    @Query("SELECT * FROM block_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BlockEventEntity>>

    @Insert
    suspend fun insert(entity: BlockEventEntity)

    @Query("DELETE FROM block_events")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM block_events WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int
}
