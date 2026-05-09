package com.kahaf.guardianshield.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kahaf.guardianshield.data.db.entity.AppLockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLockDao {

    @Query("SELECT * FROM app_locks")
    fun observeAll(): Flow<List<AppLockEntity>>

    @Query("SELECT * FROM app_locks WHERE packageName = :pkg LIMIT 1")
    suspend fun get(pkg: String): AppLockEntity?

    @Query("SELECT * FROM app_locks WHERE lockedUntilEpochMs > :nowMs")
    suspend fun getActive(nowMs: Long): List<AppLockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lock: AppLockEntity)

    @Query("DELETE FROM app_locks WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("DELETE FROM app_locks WHERE lockedUntilEpochMs < :nowMs")
    suspend fun pruneExpired(nowMs: Long)
}
