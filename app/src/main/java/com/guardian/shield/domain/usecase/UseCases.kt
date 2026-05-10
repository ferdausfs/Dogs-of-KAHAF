package com.guardian.shield.domain.usecase

import com.guardian.shield.domain.model.*
import com.guardian.shield.domain.repository.RulesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAppRulesUseCase @Inject constructor(private val repo: RulesRepository) {
    operator fun invoke(): Flow<List<AppRule>> = repo.observeAppRules()
}

class UpsertAppRuleUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke(rule: AppRule) = repo.upsertAppRule(rule)
}

class DeleteAppRuleUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke(pkg: String) = repo.deleteAppRule(pkg)
}

class GetKeywordsUseCase @Inject constructor(private val repo: RulesRepository) {
    operator fun invoke(): Flow<List<KeywordRule>> = repo.observeKeywordRules()
}

class AddKeywordUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke(keyword: String, isRegex: Boolean = false) =
        repo.upsertKeyword(KeywordRule(keyword = keyword.trim().lowercase(), isRegex = isRegex))
}

class DeleteKeywordUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke(id: Long) = repo.deleteKeyword(id)
}

class GetBlockEventsUseCase @Inject constructor(private val repo: RulesRepository) {
    operator fun invoke(limit: Int = 50): Flow<List<BlockEvent>> = repo.observeBlockEvents(limit)
}

class LogBlockEventUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke(event: BlockEvent) = repo.logBlockEvent(event)
}

class ClearBlockEventsUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke() = repo.clearBlockEvents()
}

class CountTodayBlocksUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke(): Int = repo.countTodayBlocks()
}

class GetAppRuleUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke(pkg: String): AppRule? = repo.getAppRule(pkg)
}

class GetAllKeywordsSyncUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke(): List<KeywordRule> = repo.getAllKeywordRules()
}

class GetAllAppRulesSyncUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke(): List<AppRule> = repo.getAllAppRules()
}

// ── v9 (2.0.0) — block-log export + dashboard stats ─────────────────────

class GetAllBlockEventsUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke(): List<BlockEvent> = repo.getAllBlockEvents()
}

class ObserveBlockEventsSinceUseCase @Inject constructor(private val repo: RulesRepository) {
    operator fun invoke(since: Long): Flow<List<BlockEvent>> =
        repo.observeBlockEventsSince(since)
}

// ── v9 (2.0.0) — P4-A schedule rules use cases ──────────────────────────

class GetScheduleRulesUseCase @Inject constructor(private val repo: RulesRepository) {
    operator fun invoke(): Flow<List<ScheduleRule>> = repo.observeScheduleRules()
}

class UpsertScheduleRuleUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke(rule: ScheduleRule) = repo.upsertScheduleRule(rule)
}

class DeleteScheduleRuleUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke(pkg: String) = repo.deleteScheduleRule(pkg)
}

class GetAllScheduleRulesSyncUseCase @Inject constructor(private val repo: RulesRepository) {
    suspend operator fun invoke(): List<ScheduleRule> = repo.getAllScheduleRules()
}
