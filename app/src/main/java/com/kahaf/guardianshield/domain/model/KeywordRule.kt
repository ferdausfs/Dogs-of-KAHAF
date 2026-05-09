package com.kahaf.guardianshield.domain.model

data class KeywordRule(
    val id: Long,
    val pattern: String,
    val isRegex: Boolean,
    val createdAt: Long
)
