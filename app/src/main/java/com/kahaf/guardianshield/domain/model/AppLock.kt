package com.kahaf.guardianshield.domain.model

data class AppLock(
    val packageName: String,
    val lockedUntilEpochMs: Long,
    val reason: String
) {
    fun isActive(nowMs: Long): Boolean = lockedUntilEpochMs > nowMs
    fun remainingMs(nowMs: Long): Long = (lockedUntilEpochMs - nowMs).coerceAtLeast(0L)
}
