package com.guardian.shield.util

object GuardianConstants {
    const val TEXT_THROTTLE_MS = 500L
    const val AI_THROTTLE_MS = 1_200L
    const val AI_PERIODIC_MS = 1_800L
    const val AI_FOLLOW_UP_MS = 300L
    const val SCREEN_OFF_PERIODIC_MS = 10_000L
    const val MAX_AI_SCAN_MAP = 50

    // NSFW gate threshold (max strategy on [1,5] index of NSFW model)
    const val NSFW_GATE_THRESHOLD = 0.65f

    // Threshold for semi-nudes / revealing content
    const val SOFT_NSFW_THRESHOLD = 0.55f

    // Gender threshold (kept the same)
    const val GENDER_CONFIDENCE_THRESHOLD = 0.75f

    // Early-exit ratio (legacy fast-path, retained)
    const val EARLY_EXIT_RATIO = 0.20f

    const val BLOCK_THROTTLE_MS = 3_000L
    const val MAX_THROTTLE_MAP = 50
    const val AI_DETECTOR_CLOSE_TIMEOUT_MS = 2_000L
    const val PIN_MAX_ATTEMPTS = 5
    const val PIN_LOCKOUT_MS = 30_000L
    const val MAX_NODES_BFS = 250

    // TASK 3 — AI detection strike rules
    // After the 3rd AI strike → 24h hard lock for that app.
    const val STRIKE_THRESHOLD = 3
    const val STRIKE_RESET_MS = 10 * 60 * 1_000L           // 10 min idle resets the counter
    const val AI_MAX_STRIKE_BLOCK_MS = 24 * 60 * 60 * 1_000L // 24 hours hard lock

    const val ACCESSIBILITY_WATCHDOG_MS = 5_000L
}
