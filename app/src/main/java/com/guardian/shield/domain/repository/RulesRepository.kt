package com.guardian.shield.domain.repository

import com.guardian.shield.domain.model.AppRule
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.KeywordRule
import com.guardian.shield.domain.model.ScheduleRule
import kotlinx.coroutines.flow.Flow

interface RulesRepository {
    fun observeAppRules(): Flow<List<AppRule>>
    fun observeKeywordRules(): Flow<List<KeywordRule>>
    fun observeBlockEvents(limit: Int = 50): Flow<List<BlockEvent>>
    suspend fun getAppRule(packageName: String): AppRule?
    suspend fun getAllAppRules(): List<AppRule>
    suspend fun getAllKeywordRules(): List<KeywordRule>
    suspend fun upsertAppRule(rule: AppRule)
    suspend fun deleteAppRule(packageName: String)
    suspend fun upsertKeyword(rule: KeywordRule)
    suspend fun deleteKeyword(id: Long)
    suspend fun logBlockEvent(event: BlockEvent)
    suspend fun clearBlockEvents()
    suspend fun countTodayBlocks(): Int

    // v9 (2.0.0): block log export + dashboard stats.
    suspend fun getAllBlockEvents(): List<BlockEvent>
    fun observeBlockEventsSince(since: Long): Flow<List<BlockEvent>>

    // v9 (2.0.0) — P4-A: schedule rules.
    fun observeScheduleRules(): Flow<List<ScheduleRule>>
    suspend fun getAllScheduleRules(): List<ScheduleRule>
    suspend fun getScheduleRule(packageName: String): ScheduleRule?
    suspend fun upsertScheduleRule(rule: ScheduleRule)
    suspend fun deleteScheduleRule(packageName: String)
}
