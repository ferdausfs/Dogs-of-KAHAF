package com.kahaf.guardian.domain.model

data class ProtectionState(
    val isActive: Boolean = false,
    val blockedTodayCount: Int = 0,
    val totalBlockedCount: Int = 0,
    val blockedAppsCount: Int = 0,
    val whitelistedAppsCount: Int = 0,
    val isKeywordDetectionEnabled: Boolean = true,
    val isAiDetectionEnabled: Boolean = false,
    val isStrictMode: Boolean = false,
    val delaySeconds: Int = 30
)
