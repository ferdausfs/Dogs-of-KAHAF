package com.guardian.shield.util

object GuardianConstants {
    const val TEXT_THROTTLE_MS = 400L
    const val AI_THROTTLE_MS = 500L
    const val AI_PERIODIC_MS = 1_000L
    const val AI_FOLLOW_UP_MS = 300L
    const val SCREEN_OFF_PERIODIC_MS = 10_000L
    const val MAX_AI_SCAN_MAP = 50

    // NSFW gate threshold (max strategy on [1,5] index of NSFW model)
    // ULTIMATE LEVEL: Lowered to 0.68f to catch more social media content
    const val NSFW_GATE_THRESHOLD = 0.68f

    // Threshold for semi-nudes / revealing content
    // ULTIMATE LEVEL: Lowered to 0.58f to catch more social media content
    const val SOFT_NSFW_THRESHOLD = 0.58f

    // Gender threshold (Increased for "Safe First", but dynamic in detector)
    const val GENDER_CONFIDENCE_THRESHOLD = 0.82f

    // Early-exit ratio (legacy fast-path, retained)
    const val EARLY_EXIT_RATIO = 0.20f

    const val BLOCK_THROTTLE_MS = 3_000L
    const val MAX_THROTTLE_MAP = 50
    const val AI_DETECTOR_CLOSE_TIMEOUT_MS = 2_000L
    const val PIN_MAX_ATTEMPTS = 5
    const val PIN_LOCKOUT_MS = 30_000L
    const val MAX_NODES_BFS = 250

    // TASK 3 — AI detection strike rules
    // After the 3rd AI strike → 15-minute block.
    // If blocked 3 times within 2 hours → Block for the day.
    const val STRIKE_THRESHOLD = 3
    const val STRIKE_RESET_MS = 10 * 60 * 1_000L           // 10 min idle resets the counter
    const val DEFAULT_TEMP_BLOCK_MS = 15 * 60 * 1_000L    // 15 minutes
    const val ESCALATION_WINDOW_MS = 2 * 60 * 60 * 1_000L // 2 hours
    const val ESCALATION_THRESHOLD = 3                    // 3 blocks in 2 hours
    const val DAY_BLOCK_MS = 24 * 60 * 60 * 1_000L        // 24 hours (block for the day)

    // POST-BLOCK GRACE — after a temp block expires, the app stays unlocked for
    // this long so the user can actually use the app instead of being re-blocked
    // instantly on the next AI scan. During this window AI re-blocking is paused.
    const val POST_BLOCK_GRACE_MS = 3 * 60 * 1_000L       // 3 minutes

    const val ACCESSIBILITY_WATCHDOG_MS = 5_000L

    // Liveness heartbeat from the accessibility service (produced) read by the
    // foreground service watchdog (consumed). If no beat for this long the
    // service is treated as dead even though the settings toggle is "on".
    const val ACCESSIBILITY_HEARTBEAT_MS = 2_000L
    const val ACCESSIBILITY_STALE_MS = 15_000L

    // Scroll Addiction Constants
    const val REEL_SWIPE_THRESHOLD = 15
    const val REEL_SESSION_MS = 5 * 60_000L
    const val GENERAL_SESSION_MS = 15 * 60_000L
    const val SCROLL_COOLDOWN_MS = 30 * 60_000L
    const val SCROLL_GAP_RESET_MS = 30_000L
}
