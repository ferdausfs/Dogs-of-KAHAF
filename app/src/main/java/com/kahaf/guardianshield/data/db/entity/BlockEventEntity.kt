package com.kahaf.guardianshield.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_events")
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val reason: String,
    val detail: String,
    val timestamp: Long
)
