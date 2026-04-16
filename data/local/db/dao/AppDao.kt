package com.kahaf.guardian.data.local.db.dao

import androidx.room.*
import com.kahaf.guardian.data.local.db.entity.AppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    @Query("SELECT * FROM managed_apps WHERE isBlocked = 1 ORDER BY appName ASC")
    fun getBlockedApps(): Flow<List<AppEntity>>

    @Query("SELECT * FROM managed_apps WHERE isWhitelisted = 1 ORDER BY appName ASC")
    fun getWhitelistedApps(): Flow<List<AppEntity>>

    @Query("SELECT * FROM managed_apps ORDER BY appName ASC")
    fun getAllManagedApps(): Flow<List<AppEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM managed_apps WHERE packageName = :packageName AND isBlocked = 1)")
    suspend fun isAppBlocked(packageName: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM managed_apps WHERE packageName = :packageName AND isWhitelisted = 1)")
    suspend fun isAppWhitelisted(packageName: String): Boolean

    @Query("SELECT packageName FROM managed_apps WHERE isBlocked = 1")
    suspend fun getBlockedPackages(): List<String>

    @Query("SELECT packageName FROM managed_apps WHERE isWhitelisted = 1")
    suspend fun getWhitelistedPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(app: AppEntity)

    @Query("DELETE FROM managed_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT COUNT(*) FROM managed_apps WHERE isBlocked = 1")
    fun getBlockedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM managed_apps WHERE isWhitelisted = 1")
    fun getWhitelistedCount(): Flow<Int>
}