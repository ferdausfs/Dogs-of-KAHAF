package com.guardian.shield.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val isWhitelisted: Boolean,
    val createdAt: Long
)

@Entity(tableName = "keyword_rules")
data class KeywordRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val isRegex: Boolean,
    val severity: Int,
    val enabled: Boolean
)

@Entity(tableName = "block_events")
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val reason: String,
    val matchedTerm: String?,
    val timestamp: Long
)

@Entity(tableName = "schedule_rules")
data class ScheduleRuleEntity(
    @PrimaryKey val packageName: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val enabledDaysMask: Int,
    val enabled: Boolean,
    val createdAt: Long
)

// TASK B — Pending reports for the confidence-based cooling-off system.
// When a HIGH-confidence AI detection (>= CONFIDENCE_THRESHOLD) is reported as
// "Not sensitive" (strike 1/2) or "Mark False" (strike 3 full-block), the
// unblock action is NOT applied immediately. Instead a PENDING row is inserted
// here with a scheduledApplyAt timestamp. A WorkManager worker fires at that
// time and applies the deferred action (add the snapshotted signature for
// WARNING_CARD; clearTempBlock + add the snapshotted signature for FULL_BLOCK).
// The user can view and cancel pending entries before they apply.
@Entity(tableName = "pending_reports")
data class PendingReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestampCreated: Long,
    val scheduledApplyAt: Long,
    val confidence: Float,
    // "WARNING_CARD" (strike 1/2 "Not sensitive") or "FULL_BLOCK" (strike 3 "Mark False")
    val source: String,
    // "PENDING", "APPLIED", "CANCELLED"
    val status: String,
    // Strike count at the time of the report (for logging/display)
    val strikeCount: Int = 0,
    // Delay in ms that was computed for this report (for display)
    val delayMs: Long = 0,
    // v3.6.1 — snapshot of the 8x8 image signature at REPORT time
    // (comma-separated ints). Empty when no candidate survived. The worker
    // MUST apply this stored signature and must NEVER re-read the in-memory
    // pendingCandidate (that field is overwritten by later detections and
    // lost on process death).
    val signatureCsv: String = ""
)
