package com.kahaf.guardian.domain.model

enum class BlockReason(val displayName: String) {
    APP_BLOCKED("Blocked App"),
    KEYWORD_DETECTED("Keyword Detected"),
    AI_DETECTED("AI Content Detection"),
    MANUAL("Manually Blocked")
}
