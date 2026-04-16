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
class AppRepositoryImpl @Inject constructor(
    private val appDao: AppDao
) : AppRepository {

    override fun getBlockedApps(): Flow<List<AppInfo>> {
        return appDao.getBlockedApps().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getWhitelistedApps(): Flow<List<AppInfo>> {
        return appDao.getWhitelistedApps().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllManagedApps(): Flow<List<AppInfo>> {
        return appDao.getAllManagedApps().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun isAppBlocked(packageName: String): Boolean {
        return appDao.isAppBlocked(packageName)
    }

    override suspend fun isAppWhitelisted(packageName: String): Boolean {
        return appDao.isAppWhitelisted(packageName)
    }

    override suspend fun setAppBlocked(packageName: String, appName: String, blocked: Boolean) {
        val existing = appDao.isAppBlocked(packageName) || appDao.isAppWhitelisted(packageName)
        if (blocked) {
            appDao.insertOrUpdate(
                AppEntity(
                    packageName = packageName,
                    appName = appName,
                    isBlocked = true,
                    isWhitelisted = false
                )
            )
        } else if (existing) {
            // Keep entity if whitelisted, otherwise just update
            appDao.insertOrUpdate(
                AppEntity(
                    packageName = packageName,
                    appName = appName,
                    isBlocked = false,
                    isWhitelisted = appDao.isAppWhitelisted(packageName)
                )
            )
        }
    }

    override suspend fun setAppWhitelisted(packageName: String, appName: String, whitelisted: Boolean) {
        if (whitelisted) {
            appDao.insertOrUpdate(
                AppEntity(
                    packageName = packageName,
                    appName = appName,
                    isBlocked = false,
                    isWhitelisted = true
                )
            )
        } else {
            appDao.insertOrUpdate(
                AppEntity(
                    packageName = packageName,
                    appName = appName,
                    isBlocked = appDao.isAppBlocked(packageName),
                    isWhitelisted = false
                )
            )
        }
    }

    override suspend fun getBlockedPackages(): Set<String> {
        return appDao.getBlockedPackages().toSet()
    }

    override suspend fun getWhitelistedPackages(): Set<String> {
        return appDao.getWhitelistedPackages().toSet()
    }

    private fun AppEntity.toDomain() = AppInfo(
        packageName = packageName,
        appName = appName,
        isBlocked = isBlocked,
        isWhitelisted = isWhitelisted
    )
}