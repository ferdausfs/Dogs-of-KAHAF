package com.guardian.shield.data.repository

import com.guardian.shield.data.local.db.AppRuleDao
import com.guardian.shield.data.local.db.BlockEventDao
import com.guardian.shield.data.local.db.KeywordDao
import com.guardian.shield.data.local.db.ScheduleRuleDao
import com.guardian.shield.data.local.db.toDomain
import com.guardian.shield.data.local.db.toEntity
import com.guardian.shield.domain.model.AppRule
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.KeywordRule
import com.guardian.shield.domain.model.ScheduleRule
import com.guardian.shield.domain.repository.RulesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RulesRepositoryImpl @Inject constructor(
    private val appDao: AppRuleDao,
    private val keywordDao: KeywordDao,
    private val eventDao: BlockEventDao,
    private val scheduleDao: ScheduleRuleDao
) : RulesRepository {

    override fun observeApps(): Flow<List<AppRule>> =
        appDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeKeywords(): Flow<List<KeywordRule>> =
        keywordDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeEvents(limit: Int): Flow<List<BlockEvent>> =
        eventDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeSchedules(): Flow<List<ScheduleRule>> =
        scheduleDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun countSinceFlow(since: Long): Flow<Int> = eventDao.countSinceFlow(since)
    override fun countByReasonFlow(since: Long, reason: BlockReason): Flow<Int> =
        eventDao.countByReasonFlow(since, reason.name)
    override fun topPackageFlow(since: Long): Flow<String?> = eventDao.topPackageFlow(since)

    override suspend fun upsertApp(rule: AppRule) = appDao.upsert(rule.toEntity())
    override suspend fun deleteApp(packageName: String) = appDao.delete(packageName)
    override suspend fun getApp(packageName: String): AppRule? = appDao.get(packageName)?.toDomain()
    override suspend fun blockedPackages(): Set<String> = appDao.blockedPackages().toSet()
    override suspend fun whitelistPackages(): Set<String> = appDao.whitelistPackages().toSet()

    override suspend fun upsertKeyword(rule: KeywordRule): Long = keywordDao.upsert(rule.toEntity())
    override suspend fun deleteKeyword(id: Long) = keywordDao.delete(id)
    override suspend fun enabledKeywords(): List<KeywordRule> =
        keywordDao.getEnabled().map { it.toDomain() }

    override suspend fun upsertSchedule(rule: ScheduleRule) = scheduleDao.upsert(rule.toEntity())
    override suspend fun deleteSchedule(packageName: String) = scheduleDao.delete(packageName)
    override suspend fun allSchedules(): List<ScheduleRule> =
        scheduleDao.getAll().map { it.toDomain() }

    override suspend fun logBlock(event: BlockEvent): Long = eventDao.insert(event.toEntity())
    override suspend fun deleteEvent(id: Long) = eventDao.delete(id)
    override suspend fun clearEvents() = eventDao.clear()
    override suspend fun allEvents(): List<BlockEvent> = eventDao.getAll().map { it.toDomain() }
}
