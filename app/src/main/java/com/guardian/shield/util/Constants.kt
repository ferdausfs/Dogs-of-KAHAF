package com.guardian.shield.util

object GuardianConstants {
    // Timing
    const val TEXT_THROTTLE_MS = 600L
    const val AI_THROTTLE_MS = 700L
    const val AI_PERIODIC_MS = 850L
    const val AI_FOLLOW_UP_MS = 450L
    const val SCREEN_OFF_PERIODIC_MS = 5_000L
    const val MAX_AI_SCAN_MAP = 50

    // AI
    const val NSFW_GATE_THRESHOLD = 0.6f
    const val GENDER_CONFIDENCE_THRESHOLD = 0.65f
    const val EARLY_EXIT_RATIO = 0.20f

    // Blocking
    const val BLOCK_THROTTLE_MS = 800L
    const val MAX_THROTTLE_MAP = 50

    // Close timeout
    const val AI_DETECTOR_CLOSE_TIMEOUT_MS = 2_000L

    // PIN
    const val PIN_MAX_ATTEMPTS = 5
    const val PIN_LOCKOUT_MS = 30_000L

    // Visible text BFS
    const val MAX_NODES_BFS = 250
}
