package com.kahaf.guardianshield.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "keyword_rules")
data class KeywordRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val pattern: String,
    val isRegex: Boolean,
    val createdAt: Long
)
