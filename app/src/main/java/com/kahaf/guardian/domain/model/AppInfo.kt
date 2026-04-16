package com.kahaf.guardian.domain.model

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val isBlocked: Boolean = false,
    val isWhitelisted: Boolean = false
)
