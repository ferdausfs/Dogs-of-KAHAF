package com.kahaf.guardian.domain.repository

import com.kahaf.guardian.domain.model.AppInfo
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    fun getBlockedApps(): Flow<List<AppInfo>>
    fun getWhitelistedApps(): Flow<List<AppInfo>>
    fun getAllManagedApps(): Flow<List<AppInfo>>
    suspend fun isAppBlocked(packageName: String): Boolean
    suspend fun isAppWhitelisted(packageName: String): Boolean
    suspend fun setAppBlocked(packageName: String, appName: String, blocked: Boolean)
    suspend fun setAppWhitelisted(packageName: String, appName: String, whitelisted: Boolean)
    suspend fun getBlockedPackages(): Set<String>
    suspend fun getWhitelistedPackages(): Set<String>
}
