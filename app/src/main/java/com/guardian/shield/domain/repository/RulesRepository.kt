package com.guardian.shield.domain.repository

import com.guardian.shield.domain.model.AppRule
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.KeywordRule
import com.guardian.shield.domain.model.ScheduleRule
import kotlinx.coroutines.flow.Flow

interface RulesRepository {
    fun observeApps(): Flow<List<AppRule>>
    fun observeKeywords(): Flow<List<KeywordRule>>
    fun observeEvents(limit: Int = 50): Flow<List<BlockEvent>>
    fun observeSchedules(): Flow<List<ScheduleRule>>

    // PHASE 3 (v3.5.0) — windowed reactive history for the streak card
    // (read-only; no new logging anywhere).
    fun observeEventsSince(since: Long): Flow<List<BlockEvent>>

    fun countSinceFlow(since: Long): Flow<Int>
    fun countByReasonFlow(since: Long, reason: BlockReason): Flow<Int>
    fun topPackageFlow(since: Long): Flow<String?>

    suspend fun upsertApp(rule: AppRule)
    suspend fun deleteApp(packageName: String)
    suspend fun getApp(packageName: String): AppRule?
    suspend fun blockedPackages(): Set<String>
    suspend fun whitelistPackages(): Set<String>

    suspend fun upsertKeyword(rule: KeywordRule): Long
    suspend fun deleteKeyword(id: Long)
    suspend fun enabledKeywords(): List<KeywordRule>

    suspend fun upsertSchedule(rule: ScheduleRule)
    suspend fun deleteSchedule(packageName: String)
    suspend fun allSchedules(): List<ScheduleRule>

    suspend fun logBlock(event: BlockEvent): Long
    suspend fun deleteEvent(id: Long)
    suspend fun clearEvents()
    suspend fun allEvents(): List<BlockEvent>
}
