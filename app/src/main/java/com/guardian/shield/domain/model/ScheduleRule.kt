package com.guardian.shield.domain.model

data class ScheduleRule(
    val packageName: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val enabledDays: Set<Int> = (0..6).toSet(),
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
