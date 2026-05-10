package com.guardian.shield.domain.model

/**
 * v10 (2.1.0) — Smart Tiered Detection
 *
 * Replaces the binary `isUnsafe(): Boolean` from v9. The classifier now
 * returns one of four tiers + the raw scores it used to make the decision.
 *
 *   SAFE        — clearly safe. No action.
 *   NATURAL     — neutral / portrait / landscape. No action.
 *   SUGGESTIVE  — hot / sexy but NOT explicit. LOG ONLY, do not block.
 *   EXPLICIT    — porn / hentai / nudity above the per-class threshold. BLOCK.
 */
enum class ContentTier {
    SAFE,
    NATURAL,
    SUGGESTIVE,
    EXPLICIT;

    fun shouldBlock(): Boolean = this == EXPLICIT
    fun shouldLog(): Boolean = this == SUGGESTIVE || this == EXPLICIT
}

/**
 * Full classification result returned by AiDetector.classify().
 * Includes raw scores so the AccessibilityService can drive the
 * EXPLICIT_DEBOUNCE logic and the diagnostic logger.
 */
data class ClassificationResult(
    val tier: ContentTier,
    val pornScore: Float = 0f,
    val hentaiScore: Float = 0f,
    val sexyScore: Float = 0f,
    val combinedUnsafeScore: Float = 0f
) {
    /** Highest signal across all classes — used for log display. */
    val topScore: Float
        get() = maxOf(pornScore, hentaiScore, sexyScore, combinedUnsafeScore)

    companion object {
        val SAFE = ClassificationResult(ContentTier.SAFE)
    }
}
