package com.guardianshield.app.data.repo

import com.guardianshield.app.data.db.GuardianDatabase
import com.guardianshield.app.data.model.ActivityLog
import com.guardianshield.app.data.model.AppRule
import com.guardianshield.app.data.model.KeywordFilter
import com.guardianshield.app.data.model.Schedule
import kotlinx.coroutines.flow.Flow

class GuardianRepository(db: GuardianDatabase) {
    private val ruleDao = db.appRuleDao()
    private val logDao = db.activityLogDao()
    private val keywordDao = db.keywordDao()
    private val scheduleDao = db.scheduleDao()

    // ---- App Rules ----
    fun observeRules(): Flow<List<AppRule>> = ruleDao.observeAll()
    suspend fun getRule(pkg: String) = ruleDao.get(pkg)
    suspend fun upsertRule(rule: AppRule) = ruleDao.upsert(rule.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteRule(rule: AppRule) = ruleDao.delete(rule)
    suspend fun getBlocked() = ruleDao.getAllBlocked()
    suspend fun getWhitelisted() = ruleDao.getAllWhitelisted()

    /**
     * v2: One-way rule enforcement.
     * Returns true on success, false if the user attempted to directly block a whitelisted app.
     */
    suspend fun setBlocked(pkg: String, label: String, blocked: Boolean): Boolean {
        val current = ruleDao.get(pkg)
        // One-way rule: if currently whitelisted, refuse to set blocked=true.
        if (blocked && current?.isWhitelisted == true) {
            return false
        }
        val updated = (current ?: AppRule(packageName = pkg, appLabel = label)).copy(
            appLabel = label,
            isBlocked = blocked,
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.upsert(updated)
        return true
    }

    suspend fun setWhitelisted(pkg: String, label: String, whitelisted: Boolean) {
        val current = ruleDao.get(pkg)
        val updated = (current ?: AppRule(packageName = pkg, appLabel = label)).copy(
            appLabel = label,
            isWhitelisted = whitelisted,
            // Whitelisting an app automatically turns off block (whitelist takes priority).
            isBlocked = if (whitelisted) false else current?.isBlocked ?: false,
            updatedAt = System.currentTimeMillis()
        )
        ruleDao.upsert(updated)
    }

    // ---- Activity Log ----
    fun observeRecentLogs(): Flow<List<ActivityLog>> = logDao.observeRecent()
    fun observeLogsByType(type: String): Flow<List<ActivityLog>> = logDao.observeByType(type)
    suspend fun log(entry: ActivityLog) = logDao.insert(entry)
    suspend fun pruneLogs(cutoff: Long) = logDao.pruneBefore(cutoff)

    // ---- Keywords ----
    fun observeKeywords(): Flow<List<KeywordFilter>> = keywordDao.observeAll()
    suspend fun getEnabledKeywords() = keywordDao.getEnabledKeywords()
    suspend fun upsertKeyword(k: KeywordFilter) = keywordDao.upsert(k)
    suspend fun deleteKeyword(k: KeywordFilter) = keywordDao.delete(k)

    // ---- Schedules ----
    fun observeSchedules(): Flow<List<Schedule>> = scheduleDao.observeAll()
    suspend fun getEnabledSchedules() = scheduleDao.getEnabled()
    suspend fun upsertSchedule(s: Schedule) = scheduleDao.upsert(s)
    suspend fun deleteSchedule(s: Schedule) = scheduleDao.delete(s)
}
