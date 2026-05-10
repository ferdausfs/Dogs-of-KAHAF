package com.kahaf.guardianshield.domain.model

/**
 * @param sensitivity              0f..1f, higher means stricter (lower threshold to trigger)
 * @param debounceFrames           require this many consecutive EXPLICIT frames before action
 * @param debounceWindowMs         within this rolling window
 * @param perAppBoost              extra sensitivity (added to base) per package
 * @param contentSourcePackages    packages eligible for the 15-minute auto-lock
 * @param heuristicEnabled         secondary heuristic checks alongside the model
 * @param minImageSize             ignore frames smaller than this (in px) on either dim
 * @param modelInputNormalized     true = model expects [0,1] float inputs; false = [0,255]
 *
 * v3.0.0: removed `engine` field — the real TFLite classifier is now the only
 * implementation bound by Hilt. `StubNsfwClassifier` remains in source as a
 * test double but is never wired into the production graph.
 *
 * v3.1.2 (FIX): modelInputNormalized default changed false → true.
 * The recommended HuggingFace MobileNetV2 model (and most public NSFW
 * TFLite models) expect [0,1] float inputs. With default=false the
 * preprocessor sent raw [0,255] values, producing near-zero scores for
 * every inference and making all frames appear SAFE.
 */
data class AiSettings(
    val sensitivity: Float = 0.55f,
    val debounceFrames: Int = 3,
    val debounceWindowMs: Long = 4_000L,
    val perAppBoost: Map<String, Float> = emptyMap(),
    val contentSourcePackages: Set<String> = DEFAULT_CONTENT_SOURCES,
    val heuristicEnabled: Boolean = true,
    val minImageSize: Int = 120,
    val modelInputNormalized: Boolean = true  // FIX v3.1.2: was false
) {
    fun thresholdFor(pkg: String): Float {
        val base = (1f - sensitivity).coerceIn(0.05f, 0.95f)
        val boost = perAppBoost[pkg] ?: 0f
        return (base - boost).coerceIn(0.05f, 0.95f)
    }

    companion object {
        /**
         * v3.1.3 FIX: the previous default list contained only 7 social-media
         * apps, so the AI screen-scanner silently skipped browsers, gallery,
         * file managers, video apps, etc. Result: the user opens an explicit
         * image in Chrome / their gallery / a downloads viewer and nothing
         * fires — "the app does NSFW detection but doesn't actually detect".
         * The expanded default covers the realistic set of apps that can
         * deliver user-facing media. The user can still trim the list in the
         * Detection Settings screen.
         */
        val DEFAULT_CONTENT_SOURCES: Set<String> = setOf(
            // Social
            "com.facebook.katana",
            "com.facebook.lite",
            "com.facebook.orca",                    // Messenger
            "com.instagram.android",
            "com.instagram.lite",
            "com.zhiliaoapp.musically",             // TikTok
            "com.ss.android.ugc.trill",
            "com.twitter.android",
            "com.x.android",
            "com.snapchat.android",
            "com.reddit.frontpage",
            "com.pinterest",
            "com.tumblr",
            "com.linkedin.android",
            // Messaging that hosts media / links
            "org.telegram.messenger",
            "org.thunderdog.challegram",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.discord",
            "com.viber.voip",
            // Video apps
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            // Browsers — the most common NSFW delivery surface
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.brave.browser",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.focus",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.sec.android.app.sbrowser",
            "com.duckduckgo.mobile.android",
            "com.UCMobile.intl",
            "com.kiwibrowser.browser",
            "com.vivaldi.browser",
            // Gallery / file viewers
            "com.google.android.apps.photos",
            "com.sec.android.gallery3d",
            "com.miui.gallery",
            "com.android.gallery3d",
            "com.coloros.gallery3d",
            "com.oneplus.gallery",
            "com.huawei.photos",
            "com.google.android.documentsui",
            "com.android.documentsui"
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
