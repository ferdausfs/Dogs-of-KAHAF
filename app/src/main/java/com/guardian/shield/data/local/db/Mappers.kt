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

// ── v9 (2.0.0) — P4-A: schedule rule mappers ─────────────────────────────

fun ScheduleRuleEntity.toDomain(): ScheduleRule {
    val days = mutableSetOf<Int>()
    for (i in 0..6) {
        if ((enabledDaysMask shr i) and 1 == 1) days.add(i)
    }
    return ScheduleRule(
        packageName = packageName,
        startHour = startHour,
        startMinute = startMinute,
        endHour = endHour,
        endMinute = endMinute,
        enabledDays = days,
        enabled = enabled,
        createdAt = createdAt
    )
}

fun ScheduleRule.toEntity(): ScheduleRuleEntity {
    var mask = 0
    enabledDays.forEach { d ->
        if (d in 0..6) mask = mask or (1 shl d)
    }
    return ScheduleRuleEntity(
        packageName = packageName,
        startHour = startHour,
        startMinute = startMinute,
        endHour = endHour,
        endMinute = endMinute,
        enabledDaysMask = mask,
        enabled = enabled,
        createdAt = createdAt
    )
}
