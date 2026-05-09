package com.kahaf.guardianshield.domain.repository

import com.kahaf.guardianshield.domain.model.AppRule
import com.kahaf.guardianshield.domain.model.AppRuleState
import com.kahaf.guardianshield.domain.model.InstalledApp
import kotlinx.coroutines.flow.Flow

interface AppRuleRepository {
    fun observeAll(): Flow<List<AppRule>>
    fun observeBlockedPackages(): Flow<Set<String>>
    suspend fun getInstalledApps(includeSystem: Boolean = false): List<InstalledApp>
    suspend fun setState(packageName: String, state: AppRuleState)
    suspend fun getState(packageName: String): AppRuleState
}
