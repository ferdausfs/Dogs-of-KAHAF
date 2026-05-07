package com.guardian.shield.domain.repository

import com.guardian.shield.domain.model.AppRule
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.KeywordRule
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
}
