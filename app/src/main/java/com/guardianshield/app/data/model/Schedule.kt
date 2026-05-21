package com.guardianshield.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val packageName: String,   // "*" = all
    val startMinute: Int,      // minutes from midnight
    val endMinute: Int,
    val daysMask: Int = 0x7F,  // bitmask Sun=1 .. Sat=64
    val enabled: Boolean = true
)
