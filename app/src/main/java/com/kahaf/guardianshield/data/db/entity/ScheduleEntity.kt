package com.kahaf.guardianshield.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * @param daysMask   bitmask of days (Mon=1<<0 … Sun=1<<6)
 * @param startMin   start time in minutes from 00:00 [0..1439]
 * @param endMin     end time in minutes from 00:00 [0..1439]; if endMin <= startMin
 *                   the schedule wraps over midnight
 * @param packagesCsv comma-separated list of package names
 */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val daysMask: Int,
    val startMin: Int,
    val endMin: Int,
    val packagesCsv: String,
    val enabled: Boolean
)
