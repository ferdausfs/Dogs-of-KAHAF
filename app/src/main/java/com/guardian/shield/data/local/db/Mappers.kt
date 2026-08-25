package com.guardian.shield.data.local.db

import com.guardian.shield.domain.model.AppRule
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.model.KeywordRule
import com.guardian.shield.domain.model.ScheduleRule

fun AppRuleEntity.toDomain() = AppRule(packageName, appName, isBlocked, isWhitelisted, createdAt)
fun AppRule.toEntity() = AppRuleEntity(packageName, appName, isBlocked, isWhitelisted, createdAt)

fun KeywordRuleEntity.toDomain() = KeywordRule(id, keyword, isRegex, severity, enabled)
fun KeywordRule.toEntity() = KeywordRuleEntity(id, keyword, isRegex, severity, enabled)

fun BlockEventEntity.toDomain() = BlockEvent(
    id,
    packageName,
    runCatching { BlockReason.valueOf(reason) }.getOrDefault(BlockReason.MANUAL),
    matchedTerm,
    timestamp
)
fun BlockEvent.toEntity() = BlockEventEntity(id, packageName, reason.name, matchedTerm, timestamp)

fun ScheduleRuleEntity.toDomain(): ScheduleRule {
    val days = mutableSetOf<Int>()
    for (i in 0..6) if (enabledDaysMask and (1 shl i) != 0) days.add(i)
    return ScheduleRule(packageName, startHour, startMinute, endHour, endMinute, days, enabled, createdAt, id)
}

fun ScheduleRule.toEntity(): ScheduleRuleEntity {
    var mask = 0
    for (d in enabledDays) if (d in 0..6) mask = mask or (1 shl d)
    return ScheduleRuleEntity(id, packageName, startHour, startMinute, endHour, endMinute, mask, enabled, createdAt)
}
