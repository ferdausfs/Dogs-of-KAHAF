package com.kahaf.guardianshield.domain.model

data class AppRule(
    val packageName: String,
    val state: AppRuleState,
    val addedAt: Long
)
