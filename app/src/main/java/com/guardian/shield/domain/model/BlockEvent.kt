package com.guardian.shield.domain.model

enum class BlockReason {
    APP_BLOCKED,
    KEYWORD_MATCH,
    AI_DETECTION,
    MANUAL,
    SCHEDULE_BLOCKED,
    TAMPER_ATTEMPT
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
