package com.guardian.shield.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val isWhitelisted: Boolean,
    val createdAt: Long
)

@Entity(tableName = "keyword_rules")
data class KeywordRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val isRegex: Boolean,
    val severity: Int,
    val enabled: Boolean
)

@Entity(tableName = "block_events")
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val reason: String,
    val matchedTerm: String?,
    val timestamp: Long
)

/**
 * v9 (2.0.0) — P4-A: persistence for time-based schedule rules.
 *
 * `enabledDaysMask` is a bitmask where bit N corresponds to day N
 * (0=Sun … 6=Sat). 0b1111111 == all-days.
 */
@Entity(tableName = "schedule_rules")
data class ScheduleRuleEntity(
    @PrimaryKey val packageName: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val enabledDaysMask: Int,
    val enabled: Boolean,
    val createdAt: Long
)

/**
 * v10 (2.1.0): timed block records — used by the source-based 15-min
 * auto-lock feature. When AI confirms EXPLICIT material is coming from a
 * content-source app, that app's package is added here with an expiry
 * timestamp. RulesEngine consults this on every package evaluation and
 * blocks until `expiresAt` is in the past.
 */
@Entity(tableName = "timed_blocks")
data class TimedBlockEntity(
    @PrimaryKey val packageName: String,
    val expiresAt: Long,
    /** BlockReason.name — usually AI_SOURCE_TIMED_BLOCK. */
    val reason: String,
    val createdAt: Long
)
