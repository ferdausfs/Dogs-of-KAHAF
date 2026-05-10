package com.kahaf.guardianshield.util

/**
 * Centralised tunable constants — ported from the legacy v2.x codebase
 * (`com.guardian.shield.util.GuardianConstants`) into the v3.0.0 (kahaf)
 * architecture.
 *
 * The new TFLite classifier in v3.0.0 already exposes a single
 * `sensitivity` slider (0..1) via [com.kahaf.guardianshield.domain.model.AiSettings],
 * but several detection / scheduling components want fine-grained values
 * (debounce window, source-app block duration, heavy-image boost, etc.)
 * — so we keep them here as a single source of truth.
 */
object GuardianConstants {
    // ── Accessibility throttles ───────────────────────────────────────
    const val TEXT_THROTTLE_MS    = 600L
    const val AI_THROTTLE_MS      = 700L
    const val AI_PERIODIC_MS      = 850L
    const val AI_FOLLOW_UP_MS     = 450L
    const val MAX_AI_SCAN_MAP     = 50

    /** When the screen is OFF we slow the periodic scanner way down. */
    const val SCREEN_OFF_PERIODIC_MS = 5_000L

    // ── Tiered AI thresholds (used by classifier wrappers) ────────────
    /**
     *   SAFE        score <  NATURAL_THRESHOLD                  → ignore
     *   NATURAL     NATURAL_THRESHOLD ..< SUGGESTIVE_THRESHOLD   → ignore
     *   SUGGESTIVE  SUGGESTIVE_THRESHOLD ..< EXPLICIT_THRESHOLD  → log only
     *   EXPLICIT    score >= EXPLICIT_THRESHOLD                  → BLOCK
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

    /** Default user-facing slider value. */
    const val DEFAULT_AI_THRESHOLD    = 0.78f

    // ── Sensitivity presets (UI quick-toggle) ─────────────────────────
    const val SENSITIVITY_LOW         = "LOW"        // threshold 0.85
    const val SENSITIVITY_BALANCED    = "BALANCED"   // threshold 0.78 (default)
    const val SENSITIVITY_HIGH        = "HIGH"       // threshold 0.65

    fun thresholdForSensitivity(level: String): Float = when (level) {
        SENSITIVITY_LOW       -> 0.85f
        SENSITIVITY_HIGH      -> 0.65f
        else                  -> DEFAULT_AI_THRESHOLD   // BALANCED
    }

    // ── Detection debounce (anti single-frame false-positive) ─────────
    const val EXPLICIT_DEBOUNCE_MS    = 3_000L
    const val EXPLICIT_CONFIRM_COUNT  = 2

    // ── Source-based timed block ──────────────────────────────────────
    /** When a *content source* app delivers EXPLICIT material, that app
     *  is auto-locked for this duration. No overlay arguments, no second
     *  chances — direct HOME + lock. */
    const val AI_SOURCE_BLOCK_MS      = 15L * 60L * 1000L   // 15 min

    // ── Stricter judgement on heavy-image-but-mostly-safe apps ────────
    /** Photos / Gallery / Camera / Maps boost — apps with lots of
     *  legitimate skin / portrait imagery get +0.10 added to their
     *  effective threshold so they stop spuriously triggering. */
    const val HEAVY_IMAGE_APP_THRESHOLD_BOOST = 0.10f

    // ── Blocking ──────────────────────────────────────────────────────
    const val BLOCK_THROTTLE_MS = 800L
    const val MAX_THROTTLE_MAP  = 50

    // ── AiDetector close() ANR guard ──────────────────────────────────
    const val AI_DETECTOR_CLOSE_TIMEOUT_MS = 2_000L

    // ── Reflection delay (Delay-Unlock screen) ────────────────────────
    /** Default seconds the user must wait on the "reflect" screen before
     *  toggling protection off / unlocking sensitive settings. */
    const val DEFAULT_DELAY_SECONDS   = 30
}
