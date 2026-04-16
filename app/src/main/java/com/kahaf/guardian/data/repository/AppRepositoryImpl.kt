package com.kahaf.guardian.data.repository

import com.kahaf.guardian.data.local.db.dao.AppDao
import com.kahaf.guardian.data.local.db.entity.AppEntity
import com.kahaf.guardian.domain.model.AppInfo
import com.kahaf.guardian.domain.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepositoryImpl @Inject constructor(private val dao: AppDao) : AppRepository {
    override fun getBlockedApps(): Flow<List<AppInfo>> = dao.getBlockedApps().map { it.map { e -> e.toModel() } }
    override fun getWhitelistedApps(): Flow<List<AppInfo>> = dao.getWhitelistedApps().map { it.map { e -> e.toModel() } }
    override fun getAllManagedApps(): Flow<List<AppInfo>> = dao.getAllManagedApps().map { it.map { e -> e.toModel() } }
    override suspend fun isAppBlocked(packageName: String) = dao.isAppBlocked(packageName)
    override suspend fun isAppWhitelisted(packageName: String) = dao.isAppWhitelisted(packageName)
    override suspend fun setAppBlocked(packageName: String, appName: String, blocked: Boolean) {
        if (blocked) dao.insertOrUpdate(AppEntity(packageName, appName, isBlocked = true, isWhitelisted = false))
        else dao.insertOrUpdate(AppEntity(packageName, appName, isBlocked = false, isWhitelisted = dao.isAppWhitelisted(packageName)))
    }
    override suspend fun setAppWhitelisted(packageName: String, appName: String, whitelisted: Boolean) {
        if (whitelisted) dao.insertOrUpdate(AppEntity(packageName, appName, isBlocked = false, isWhitelisted = true))
        else dao.insertOrUpdate(AppEntity(packageName, appName, isBlocked = dao.isAppBlocked(packageName), isWhitelisted = false))
    }
    override suspend fun getBlockedPackages() = dao.getBlockedPackages().toSet()
    override suspend fun getWhitelistedPackages() = dao.getWhitelistedPackages().toSet()
    private fun AppEntity.toModel() = AppInfo(packageName, appName, isBlocked = isBlocked, isWhitelisted = isWhitelisted)
}
