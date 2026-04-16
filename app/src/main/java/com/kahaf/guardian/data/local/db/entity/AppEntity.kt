package com.kahaf.guardian.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "managed_apps")
data class AppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isBlocked: Boolean = false,
    val isWhitelisted: Boolean = false,
    val addedTimestamp: Long = System.currentTimeMillis()
)
