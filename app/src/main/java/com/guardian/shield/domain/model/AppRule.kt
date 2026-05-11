package com.guardian.shield.domain.model

data class AppRule(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val isWhitelisted: Boolean,
    val createdAt: Long
)
