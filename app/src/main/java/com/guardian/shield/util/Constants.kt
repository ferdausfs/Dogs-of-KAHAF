package com.guardian.shield.util

object GuardianConstants {
    const val TEXT_THROTTLE_MS = 600L
    const val AI_THROTTLE_MS = 3_000L
    const val AI_PERIODIC_MS = 3_500L
    const val AI_FOLLOW_UP_MS = 450L
    const val SCREEN_OFF_PERIODIC_MS = 10_000L
    const val MAX_AI_SCAN_MAP = 50

    // ✅ Fix: NSFW gate থেকে 0.6 → 0.45
    // nsfw_model [1,5] এ MAX strategy তে 0.45 যথেষ্ট sensitive
    const val NSFW_GATE_THRESHOLD = 0.45f

    // Gender threshold same
    const val GENDER_CONFIDENCE_THRESHOLD = 0.60f

    // ✅ Fix: guardian_model early exit এখন threshold - 0.2 এ
    // তাই EARLY_EXIT_RATIO আর দরকার নেই but keep it
    const val EARLY_EXIT_RATIO = 0.20f

    const val BLOCK_THROTTLE_MS = 3_000L
    const val MAX_THROTTLE_MAP = 50
    const val AI_DETECTOR_CLOSE_TIMEOUT_MS = 2_000L
    const val PIN_MAX_ATTEMPTS = 5
    const val PIN_LOCKOUT_MS = 30_000L
    const val MAX_NODES_BFS = 250
    // ===== TASK 3: AI detection max 3 strikes -> 24h hard lock =====
    const val STRIKE_THRESHOLD = 3
    const val STRIKE_RESET_MS = 10 * 60 * 1_000L
    // After the 3rd AI strike, the app is hard-locked for 24 hours.
    // 24h is measured from the moment of the 3rd block, NOT until midnight.
    const val AI_MAX_STRIKE_BLOCK_MS = 24 * 60 * 60 * 1_000L
    const val ACCESSIBILITY_WATCHDOG_MS = 5_000L
}