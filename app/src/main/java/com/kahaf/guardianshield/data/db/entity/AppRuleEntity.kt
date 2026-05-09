package com.kahaf.guardianshield.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-package rule. State is stored as a String so we don't need a custom
 * type-converter migration just to support it.
 *
 *  - "BLOCKED"     : foreground triggers BlockOverlay
 *  - "WHITELISTED" : never blocked, exempt from keyword/AI/scheduling
 *  - "NORMAL"      : default; subject to dynamic rules (schedules, AI…)
 */
@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val state: String,
    val addedAt: Long
)
