package com.guardian.shield.domain.model

data class AppRule(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val isWhitelisted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
