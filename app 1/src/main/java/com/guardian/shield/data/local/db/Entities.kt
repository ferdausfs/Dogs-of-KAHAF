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
