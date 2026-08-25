package com.guardian.shield.domain.model

data class ScheduleRule(
    val packageName: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val enabledDays: Set<Int> = (0..6).toSet(),
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** R7.7 — multi-window: DB row id (0 = not yet inserted). Appended last
     *  so existing positional constructions stay source-compatible. */
    val id: Long = 0
)
