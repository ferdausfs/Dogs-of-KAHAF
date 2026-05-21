package com.guardianshield.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_log")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val appLabel: String = "",
    val eventType: String,    // BLOCK, AI_WARN, AI_BLOCK_24H, SCROLL_REMINDER, KEYWORD, SCHEDULE, TAMPER
    val details: String = "",
    val strikeCount: Int = 0
)
