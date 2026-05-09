package com.kahaf.guardianshield.domain.model

import java.util.Calendar

data class Schedule(
    val id: Long,
    val label: String,
    val daysMask: Int,
    val startMin: Int,
    val endMin: Int,
    val packages: List<String>,
    val enabled: Boolean
) {
    fun isActiveAt(nowEpochMs: Long): Boolean {
        if (!enabled) return false
        val cal = Calendar.getInstance().apply { timeInMillis = nowEpochMs }
        // Calendar.MONDAY = 2 ... SUNDAY = 1
        val dayBit = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1 shl 0
            Calendar.TUESDAY -> 1 shl 1
            Calendar.WEDNESDAY -> 1 shl 2
            Calendar.THURSDAY -> 1 shl 3
            Calendar.FRIDAY -> 1 shl 4
            Calendar.SATURDAY -> 1 shl 5
            Calendar.SUNDAY -> 1 shl 6
            else -> 0
        }
        if (daysMask and dayBit == 0) return false
        val minOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (endMin > startMin) {
            minOfDay in startMin until endMin
        } else {
            // wraps over midnight
            minOfDay >= startMin || minOfDay < endMin
        }
    }
}

object DaysMask {
    const val MON = 1 shl 0
    const val TUE = 1 shl 1
    const val WED = 1 shl 2
    const val THU = 1 shl 3
    const val FRI = 1 shl 4
    const val SAT = 1 shl 5
    const val SUN = 1 shl 6
    const val ALL = MON or TUE or WED or THU or FRI or SAT or SUN
}
