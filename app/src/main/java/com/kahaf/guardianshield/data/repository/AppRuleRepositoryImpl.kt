package com.kahaf.guardianshield.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.kahaf.guardianshield.data.db.dao.AppRuleDao
import com.kahaf.guardianshield.data.db.entity.AppRuleEntity
import com.kahaf.guardianshield.domain.model.AppRule
import com.kahaf.guardianshield.domain.model.AppRuleState
import com.kahaf.guardianshield.domain.model.InstalledApp
import com.kahaf.guardianshield.domain.repository.AppRuleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRuleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AppRuleDao
) : AppRuleRepository {

    override fun observeAll(): Flow<List<AppRule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeBlockedPackages(): Flow<Set<String>> =
        dao.observeBlockedPackages().map { it.toHashSet() }

    override suspend fun getInstalledApps(includeSystem: Boolean): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val flags = PackageManager.GET_META_DATA
            val pkgs = pm.getInstalledApplications(flags)
            pkgs.asSequence()
                .filter { includeSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM == 0) ||
                          (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0) }
                .filter { it.packageName != context.packageName }
                .map {
                    InstalledApp(
                        packageName = it.packageName,
                        label = pm.getApplicationLabel(it).toString(),
                        isSystemApp = (it.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
        }

    override suspend fun setState(packageName: String, state: AppRuleState) {
        if (state == AppRuleState.NORMAL) {
            dao.delete(packageName)
        } else {
            dao.upsert(
                AppRuleEntity(
                    packageName = packageName,
                    state = state.name,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun getState(packageName: String): AppRuleState =
        AppRuleState.fromStringOrDefault(dao.get(packageName)?.state)

    private fun AppRuleEntity.toDomain() = AppRule(
        packageName = packageName,
        state = AppRuleState.fromStringOrDefault(state),
        addedAt = addedAt
    )
}
