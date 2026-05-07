package com.guardian.shield.domain.model

data class KeywordRule(
    val id: Long = 0,
    val keyword: String,
    val isRegex: Boolean = false,
    val severity: Int = 1,
    val enabled: Boolean = true
)
