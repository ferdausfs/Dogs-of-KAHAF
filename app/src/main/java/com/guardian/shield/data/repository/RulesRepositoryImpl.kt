package com.guardian.shield.data.repository

import com.guardian.shield.data.local.db.*
import com.guardian.shield.domain.model.*
import com.guardian.shield.domain.repository.RulesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RulesRepositoryImpl @Inject constructor(
    private val appDao: AppRuleDao,
    private val kwDao: KeywordDao,
    private val evtDao: BlockEventDao
) : RulesRepository {

    override fun observeAppRules(): Flow<List<AppRule>> =
        appDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeKeywordRules(): Flow<List<KeywordRule>> =
        kwDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeBlockEvents(limit: Int): Flow<List<BlockEvent>> =
        evtDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getAppRule(packageName: String): AppRule? =
        appDao.getByPackage(packageName)?.toDomain()

    override suspend fun getAllAppRules(): List<AppRule> = appDao.getAll().map { it.toDomain() }
    override suspend fun getAllKeywordRules(): List<KeywordRule> = kwDao.getAllEnabled().map { it.toDomain() }
    override suspend fun upsertAppRule(rule: AppRule) = appDao.upsert(rule.toEntity())
    override suspend fun deleteAppRule(packageName: String) = appDao.deleteByPackage(packageName)
    override suspend fun upsertKeyword(rule: KeywordRule) = kwDao.upsert(rule.toEntity())
    override suspend fun deleteKeyword(id: Long) = kwDao.deleteById(id)
    override suspend fun logBlockEvent(event: BlockEvent) = evtDao.insert(event.toEntity())
    override suspend fun clearBlockEvents() = evtDao.deleteAll()

    override suspend fun countTodayBlocks(): Int {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        return evtDao.countSince(cal.timeInMillis)
    }
}
