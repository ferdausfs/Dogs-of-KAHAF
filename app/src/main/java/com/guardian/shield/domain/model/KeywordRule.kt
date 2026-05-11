package com.guardian.shield.domain.model

data class KeywordRule(
    val id: Long,
    val keyword: String,
    val isRegex: Boolean,
    val severity: Int,
    val enabled: Boolean
)
