package com.guardian.shield.util

object GuardianConstants {
    // Timing
    const val TEXT_THROTTLE_MS = 600L
    const val AI_THROTTLE_MS = 3_000L       // ছিল 700ms — AI প্রতি 3s এর বেশি না
    const val AI_PERIODIC_MS = 3_500L       // ছিল 850ms — periodic scan 3.5s
    const val AI_FOLLOW_UP_MS = 450L
    const val SCREEN_OFF_PERIODIC_MS = 10_000L  // ছিল 5s
    const val MAX_AI_SCAN_MAP = 50

    // AI
    const val NSFW_GATE_THRESHOLD = 0.6f
    const val GENDER_CONFIDENCE_THRESHOLD = 0.65f
    const val EARLY_EXIT_RATIO = 0.20f

    // Blocking
    const val BLOCK_THROTTLE_MS = 3_000L   // ছিল 800ms — same pkg 3s এর মধ্যে দুইবার block না
    const val MAX_THROTTLE_MAP = 50

    // Close timeout
    const val AI_DETECTOR_CLOSE_TIMEOUT_MS = 2_000L

    // PIN
    const val PIN_MAX_ATTEMPTS = 5
    const val PIN_LOCKOUT_MS = 30_000L

    // Visible text BFS
    const val MAX_NODES_BFS = 250
}