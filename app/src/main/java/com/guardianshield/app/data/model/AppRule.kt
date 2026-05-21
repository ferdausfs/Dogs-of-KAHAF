package com.guardianshield.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents per-app blocking rules.
 *
 * One-way rule (v2):
 *   - isWhitelisted=true  → cannot be directly blocked (block switch disabled).
 *   - User must first disable whitelist, then enable block.
 */
@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val packageName: String,
    val appLabel: String = "",
    val isBlocked: Boolean = false,
    val isWhitelisted: Boolean = false,
    val isLocked: Boolean = false,       // admin-locked (cannot change without PIN)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
