package com.kahaf.guardianshield.domain.model

/**
 * @param sensitivity              0f..1f, higher means stricter (lower threshold to trigger)
 * @param debounceFrames           require this many consecutive EXPLICIT frames before action
 * @param debounceWindowMs         within this rolling window
 * @param perAppBoost              extra sensitivity (added to base) per package
 * @param contentSourcePackages    packages eligible for the 15-minute auto-lock
 * @param engine                   "real" or "stub"
 */
data class AiSettings(
    val sensitivity: Float = 0.55f,
    val debounceFrames: Int = 3,
    val debounceWindowMs: Long = 4_000L,
    val perAppBoost: Map<String, Float> = emptyMap(),
    val contentSourcePackages: Set<String> = DEFAULT_CONTENT_SOURCES,
    val engine: String = "stub"
) {
    fun thresholdFor(pkg: String): Float {
        val base = (1f - sensitivity).coerceIn(0.05f, 0.95f)
        val boost = perAppBoost[pkg] ?: 0f
        return (base - boost).coerceIn(0.05f, 0.95f)
    }

    companion object {
        val DEFAULT_CONTENT_SOURCES: Set<String> = setOf(
            "com.facebook.katana",
            "com.instagram.android",
            "com.zhiliaoapp.musically",   // TikTok
            "com.twitter.android",
            "com.x.android",
            "com.reddit.frontpage",
            "com.pinterest"
        )

        val HEAVY_IMAGE_APPS: Set<String> = setOf(
            "com.facebook.katana",
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.twitter.android",
            "com.x.android",
            "com.pinterest"
        )
    }
}
