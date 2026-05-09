package com.guardian.shield.domain.model

data class BlockEvent(
    val id: Long = 0,
    val packageName: String,
    val reason: BlockReason,
    val matchedTerm: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * v10 (2.1.0): added AI_SOURCE_TIMED_BLOCK for the new 15-minute auto-lock
 * when a content-source app (Facebook / Instagram / etc.) is the verified
 * source of EXPLICIT material.
 *
 * v9 (2.0.0): added SCHEDULE_BLOCKED for P4-A time-based schedule rules.
 */
enum class BlockReason {
    APP_BLOCKED,
    KEYWORD_MATCH,
    AI_DETECTION,
    MANUAL,
    SCHEDULE_BLOCKED,
    AI_SOURCE_TIMED_BLOCK   // v10: 15-min targeted lock after EXPLICIT detection
}
