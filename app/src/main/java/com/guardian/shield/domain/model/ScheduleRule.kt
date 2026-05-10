package com.guardian.shield.domain.model

/**
 * v9 (2.0.0) — P4-A: time-based schedule blocking.
 *
 * Allows blocking specific apps during a recurring time window (e.g.
 * social media blocked 22:00–06:00). The window may wrap across midnight.
 *
 * Days follow the convention: 0 = Sunday … 6 = Saturday (matching
 * `Calendar.DAY_OF_WEEK - 1`).
 */
data class ScheduleRule(
    val packageName: String,
    val startHour: Int,    // 0–23
    val startMinute: Int,  // 0–59
    val endHour: Int,      // 0–23
    val endMinute: Int,    // 0–59
    val enabledDays: Set<Int> = setOf(0, 1, 2, 3, 4, 5, 6),
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
