package com.kahaf.guardian.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_logs")
data class BlockLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val reason: String,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
