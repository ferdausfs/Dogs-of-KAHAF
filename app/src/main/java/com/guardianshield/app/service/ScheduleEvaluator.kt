package com.guardianshield.app.service

import com.guardianshield.app.data.repo.GuardianRepository
import java.util.Calendar

object ScheduleEvaluator {
    suspend fun isInBlockingWindow(repo: GuardianRepository, pkg: String): Boolean {
        val schedules = repo.getEnabledSchedules()
        if (schedules.isEmpty()) return false
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        // Calendar.SUNDAY = 1 .. SATURDAY = 7 → bit positions 0..6
        val dayBit = 1 shl (cal.get(Calendar.DAY_OF_WEEK) - 1)

        for (s in schedules) {
            if ((s.daysMask and dayBit) == 0) continue
            if (s.packageName != "*" && s.packageName != pkg) continue
            val inWindow =
                if (s.startMinute <= s.endMinute) nowMin in s.startMinute..s.endMinute
                else nowMin >= s.startMinute || nowMin <= s.endMinute
            if (inWindow) return true
        }
        return false
    }
}
