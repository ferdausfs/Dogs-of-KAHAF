package com.guardian.shield.domain.model

enum class BlockReason {
    APP_BLOCKED,
    KEYWORD_MATCH,
    AI_DETECTION,
    MANUAL,
    SCHEDULE_BLOCKED,
    TAMPER_ATTEMPT,
    // v2.5.4 — audit-only event: the user reported a strike-1/2 warning as
    // "not sensitive". Logged to block_events for later human review. It has
    // NO effect on detection (AiDetector), learning (FalsePositiveMemory), or
    // strike counting (TempBlockManager).
    NOT_SENSITIVE
}

data class BlockEvent(
    val id: Long,
    val packageName: String,
    val reason: BlockReason,
    val matchedTerm: String?,
    val timestamp: Long
)

sealed class DetectionResult {
    data object Allow : DetectionResult()
    data class Block(val reason: BlockReason, val detail: String) : DetectionResult()
}
