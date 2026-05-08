package com.guardian.shield.util

/**
 * v9 (2.0.0) — P5-B: centralised tunable constants.
 *
 * All throttle values, thresholds, and timing constants previously scattered
 * across files now live here. Update once, propagate everywhere.
 *
 * Existing in-file constants are preserved (kept private inside their
 * respective classes) to avoid breaking the build chain or back-compat —
 * they now reference these public defaults.
 */
object GuardianConstants {
    // ── Accessibility throttles ───────────────────────────────────────
    const val TEXT_THROTTLE_MS    = 600L
    const val AI_THROTTLE_MS      = 700L
    const val AI_PERIODIC_MS      = 850L
    const val AI_FOLLOW_UP_MS     = 450L
    const val MAX_AI_SCAN_MAP     = 50

    // P1-E: when the screen is OFF we slow the periodic scanner way down.
    const val SCREEN_OFF_PERIODIC_MS = 5_000L

    // ── AI thresholds ─────────────────────────────────────────────────
    const val NSFW_GATE_THRESHOLD          = 0.6f
    const val GENDER_CONFIDENCE_THRESHOLD  = 0.65f

    // P1-D: early-exit ratio — if the primary (full-image) NSFW score is
    // below threshold * EARLY_EXIT_RATIO, we skip the 3 follow-up crops.
    const val EARLY_EXIT_RATIO = 0.20f

    // ── Blocking ──────────────────────────────────────────────────────
    const val BLOCK_THROTTLE_MS = 800L
    const val MAX_THROTTLE_MAP  = 50

    // ── AiDetector close() ANR guard (P2-A) ───────────────────────────
    const val AI_DETECTOR_CLOSE_TIMEOUT_MS = 2_000L
}
