package com.kahaf.guardianshield.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Source-based 15-minute auto-lock record. Persisted so it survives process death.
 */
@Entity(tableName = "app_locks")
data class AppLockEntity(
    @PrimaryKey val packageName: String,
    val lockedUntilEpochMs: Long,
    val reason: String
)
