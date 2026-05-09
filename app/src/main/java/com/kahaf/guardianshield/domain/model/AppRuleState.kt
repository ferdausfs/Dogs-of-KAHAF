package com.kahaf.guardianshield.domain.model

/** Tri-state per-package rule. Persisted as String in Room (see AppRuleEntity). */
enum class AppRuleState {
    BLOCKED,
    WHITELISTED,
    NORMAL;

    companion object {
        fun fromStringOrDefault(raw: String?): AppRuleState =
            values().firstOrNull { it.name == raw } ?: NORMAL
    }
}
