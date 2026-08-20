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

    // v3.3.0 — the strike-1/2 warning card is user-dismissed (acknowledge /
    // tap-card / Not-sensitive). The old 3.5s auto-dismiss constant is gone:
    // the inter-strike gate is now an explicit `isWarningCardShowing` flag on
    // TempBlockManager, not a fixed duration. This safety fallback is ONLY a
    // leak/stuck-card net (phone locked, view leak) — if it fires the card is
    // auto-dismissed AND the strike gate reopens, logged with Timber.w.
    const val STRIKE_WARNING_SAFETY_FALLBACK_MS = 40_000L

    // Burst dedup after a counted strike so two concurrent scan paths
    // (content-aware region + full-frame) cannot increment twice in the same
    // tick. Independent of the warning-card visibility flag.
    const val STRIKE_BURST_DEDUP_MS = 1_000L
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

    // TASK B — Confidence-based cooling-off system.
    // AI detections with confidence >= this threshold are routed into a
    // cooling-off queue instead of applying the user's "Not sensitive" / "Mark
    // False" report immediately. Low-confidence (< threshold) detections apply
    // instantly (preserving the existing instant behaviour).
    const val CONFIDENCE_THRESHOLD = 0.82f

    // Escalating delay: rolling 24-hour window per package.
    // 1st high-confidence report → 2 h delay; 2nd → 4 h; 3rd → 8 h; 4th → 16 h;
    // 5th+ → capped at 24 h. Formula: min(BASE * 2^(n-1), MAX).
    const val COOLING_BASE_DELAY_MS = 2L * 60 * 60 * 1000    // 2 hours
    const val COOLING_MAX_DELAY_MS = 24L * 60 * 60 * 1000    // 24 hours
    const val COOLING_WINDOW_MS = 24L * 60 * 60 * 1000       // 24-hour rolling window
}
