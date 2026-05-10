package com.guardian.shield.domain.model

data class BlockEvent(
    val id: Long = 0,
    val packageName: String,
    val reason: BlockReason,
    val matchedTerm: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class BlockReason { APP_BLOCKED, KEYWORD_MATCH, AI_DETECTION, MANUAL }
