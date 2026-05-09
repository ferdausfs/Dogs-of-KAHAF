package com.kahaf.guardianshield.domain.model

data class BlockEvent(
    val id: Long,
    val packageName: String,
    val reason: BlockReason,
    val detail: String,
    val timestamp: Long
)

enum class BlockReason {
    APP_RULE,        // package is in BLOCKED state
    KEYWORD,         // matched keyword on screen
    SCHEDULE,        // currently inside a recurring window
    AI_NSFW,         // AI classifier matched EXPLICIT
    AUTO_LOCK;       // 15-minute source-based lock

    companion object {
        fun fromStringOrDefault(s: String?): BlockReason =
            values().firstOrNull { it.name == s } ?: APP_RULE
    }
}
