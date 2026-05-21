package com.guardianshield.app.manager

import android.util.Log

/**
 * Lightweight on-device AI-style content classifier.
 *
 * Strategy: keyword/heuristic scoring on captured text from AccessibilityService.
 * (A full on-device ML model can be plugged in later — this provides the strike
 * pipeline contract used by TempBlockManager.)
 */
object AiContentDetector {

    private val flaggedKeywords = listOf(
        // Bengali
        "যৌন", "অশ্লীল", "নগ্ন", "পর্ণ",
        // English
        "porn", "nude", "sex", "xxx", "onlyfans", "nsfw",
        // Arabic transliterated
        "haram", "fahisha"
    )

    /**
     * @return confidence 0.0..1.0 — caller treats >= 0.7 as a strike.
     */
    fun scoreText(text: String?): Double {
        if (text.isNullOrBlank()) return 0.0
        val lower = text.lowercase()
        var hits = 0
        for (k in flaggedKeywords) {
            if (lower.contains(k)) hits++
        }
        val score = when {
            hits == 0 -> 0.0
            hits == 1 -> 0.55
            hits == 2 -> 0.75
            else -> 0.9
        }
        if (score > 0.0) Log.d("AiDetector", "score=$score hits=$hits")
        return score
    }

    fun isStrike(text: String?): Boolean = scoreText(text) >= 0.7
}
