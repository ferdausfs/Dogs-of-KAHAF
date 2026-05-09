package com.kahaf.guardianshield.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kahaf.guardianshield.data.db.entity.AppRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {

    @Query("SELECT * FROM app_rules")
    fun observeAll(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE state = :state")
    fun observeByState(state: String): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg LIMIT 1")
    suspend fun get(pkg: String): AppRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AppRuleEntity)

    @Query("DELETE FROM app_rules WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("SELECT packageName FROM app_rules WHERE state = 'BLOCKED'")
    fun observeBlockedPackages(): Flow<List<String>>
}
