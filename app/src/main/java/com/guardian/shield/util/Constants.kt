package com.guardian.shield.util

/**
 * v10 (2.1.0) — Smart Tiered Detection
 *
 * 🎯 NEW THIS RELEASE:
 *   • Three-tier classification (SAFE / NATURAL / SUGGESTIVE / EXPLICIT).
 *   • Default threshold raised to 0.78 — far fewer false positives on
 *     natural / portrait / landscape content.
 *   • Per-class block thresholds (porn / hentai / sexy treated separately).
 *   • EXPLICIT_DEBOUNCE — block only after 2 consecutive explicit
 *     detections within 3 s. Single-frame false positives no longer fire.
 *   • Source-based timed-block (15 min) when a content-source app
 *     (Facebook / Instagram / etc.) is the verified source of NSFW.
 *
 * v9 (2.0.0) — P5-B: centralised tunable constants.
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

    // ── AI thresholds (legacy single-tier — kept for back-compat) ─────
    /** v10: gate raised 0.6 → 0.62 to match the less-aggressive policy. */
    const val NSFW_GATE_THRESHOLD          = 0.62f
    const val GENDER_CONFIDENCE_THRESHOLD  = 0.65f

    // P1-D: early-exit ratio — if the primary (full-image) NSFW score is
    // below threshold * EARLY_EXIT_RATIO, we skip the 3 follow-up crops.
    const val EARLY_EXIT_RATIO = 0.20f

    // ── v10 (2.1.0): TIERED THRESHOLDS ────────────────────────────────
    /**
     *   SAFE        score <  NATURAL_THRESHOLD                  → ignore
     *   NATURAL     NATURAL_THRESHOLD ..< SUGGESTIVE_THRESHOLD   → ignore
     *   SUGGESTIVE  SUGGESTIVE_THRESHOLD ..< EXPLICIT_THRESHOLD  → log only
     *   EXPLICIT    score >= EXPLICIT_THRESHOLD                  → BLOCK
     *
     * Per-class hard thresholds let us reject sexy-only frames without
     * blocking, while still catching porn/hentai with high confidence.
     */
    const val NATURAL_THRESHOLD       = 0.30f
    const val SUGGESTIVE_THRESHOLD    = 0.55f
    const val EXPLICIT_THRESHOLD      = 0.75f

    /** Per-class explicit cut-offs. */
    const val PORN_BLOCK_THRESHOLD    = 0.78f
    const val HENTAI_BLOCK_THRESHOLD  = 0.75f
    /** Sexy alone never blocks — but anything above this is logged. */
    const val SEXY_LOG_THRESHOLD      = 0.60f
    /** Combined unsafe score — must exceed this for an EXPLICIT verdict. */
    const val COMBINED_EXPLICIT_THRESHOLD = 0.75f

    /** Default user-facing slider value (was 0.7 in v9 — too aggressive). */
    const val DEFAULT_AI_THRESHOLD    = 0.78f

    // ── Sensitivity presets (UI ChipGroup) ────────────────────────────
    /** Only obvious / unambiguous porn. Use if false-positive complaints. */
    const val SENSITIVITY_LOW         = "LOW"        // threshold 0.85
    /** Recommended balance. */
    const val SENSITIVITY_BALANCED    = "BALANCED"   // threshold 0.78 (default)
    /** Catches more, may produce some false positives. */
    const val SENSITIVITY_HIGH        = "HIGH"       // threshold 0.65

    fun thresholdForSensitivity(level: String): Float = when (level) {
        SENSITIVITY_LOW       -> 0.85f
        SENSITIVITY_HIGH      -> 0.65f
        else                  -> DEFAULT_AI_THRESHOLD   // BALANCED
    }

    // ── v10: detection debounce (anti single-frame false-positive) ────
    /** Time window during which two EXPLICIT hits must occur to block. */
    const val EXPLICIT_DEBOUNCE_MS    = 3_000L
    /** Number of EXPLICIT classifications required before block fires. */
    const val EXPLICIT_CONFIRM_COUNT  = 2

    // ── v10: source-based timed block ─────────────────────────────────
    /** When a *content source* app delivers EXPLICIT material, that app
     *  is auto-locked for this duration. No overlay arguments, no second
     *  chances — direct HOME + lock. */
    const val AI_SOURCE_BLOCK_MS      = 15L * 60L * 1000L   // 15 min

    // ── v10: stricter judgement on heavy-image-but-mostly-safe apps ───
    /** Photos / Gallery / Camera / Maps boost — apps with lots of
     *  legitimate skin / portrait imagery get +0.10 added to their
     *  effective threshold so they stop spuriously triggering. */
    const val HEAVY_IMAGE_APP_THRESHOLD_BOOST = 0.10f

    // ── Blocking ──────────────────────────────────────────────────────
    const val BLOCK_THROTTLE_MS = 800L
    const val MAX_THROTTLE_MAP  = 50

    // ── AiDetector close() ANR guard (P2-A) ───────────────────────────
    const val AI_DETECTOR_CLOSE_TIMEOUT_MS = 2_000L
}
