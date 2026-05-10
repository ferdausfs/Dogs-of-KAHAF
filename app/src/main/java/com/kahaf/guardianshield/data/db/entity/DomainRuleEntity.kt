package com.kahaf.guardianshield.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "domain_rules")
data class DomainRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val domain: String,
    val createdAt: Long
)
