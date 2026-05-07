package com.guardian.shield.data.local.db

import com.guardian.shield.domain.model.*

fun AppRuleEntity.toDomain() = AppRule(0, packageName, appName, isBlocked, isWhitelisted, createdAt)
fun AppRule.toEntity() = AppRuleEntity(packageName, appName, isBlocked, isWhitelisted, createdAt)

fun KeywordRuleEntity.toDomain() = KeywordRule(id, keyword, isRegex, severity, enabled)
fun KeywordRule.toEntity() = KeywordRuleEntity(id, keyword, isRegex, severity, enabled)

fun BlockEventEntity.toDomain() = BlockEvent(
    id, packageName,
    runCatching { BlockReason.valueOf(reason) }.getOrDefault(BlockReason.MANUAL),
    matchedTerm, timestamp
)
fun BlockEvent.toEntity() = BlockEventEntity(id, packageName, reason.name, matchedTerm, timestamp)
