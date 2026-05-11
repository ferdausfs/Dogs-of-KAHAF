package com.guardian.shield.util

object GuardianConstants {
    const val TEXT_THROTTLE_MS = 600L
    const val AI_THROTTLE_MS = 3_000L
    const val AI_PERIODIC_MS = 3_500L
    const val AI_FOLLOW_UP_MS = 450L
    const val SCREEN_OFF_PERIODIC_MS = 10_000L
    const val MAX_AI_SCAN_MAP = 50
    const val NSFW_GATE_THRESHOLD = 0.6f
    const val GENDER_CONFIDENCE_THRESHOLD = 0.65f
    const val EARLY_EXIT_RATIO = 0.20f
    const val BLOCK_THROTTLE_MS = 3_000L
    const val MAX_THROTTLE_MAP = 50
    const val AI_DETECTOR_CLOSE_TIMEOUT_MS = 2_000L
    const val PIN_MAX_ATTEMPTS = 5
    const val PIN_LOCKOUT_MS = 30_000L
    const val MAX_NODES_BFS = 250
    const val STRIKE_THRESHOLD = 2
    const val STRIKE_RESET_MS = 10 * 60 * 1_000L
    const val ACCESSIBILITY_WATCHDOG_MS = 5_000L
}