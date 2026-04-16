package com.kahaf.guardian.util

object Constants {
    const val NOTIFICATION_CHANNEL_ID = "kahaf_protection_channel"
    const val NOTIFICATION_ID = 1001
    const val BLOCK_SCREEN_REQUEST = "block_screen_request"
    const val EXTRA_BLOCK_REASON = "extra_block_reason"
    const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
    const val EXTRA_PURPOSE = "extra_purpose"
    const val PURPOSE_SETTINGS = "settings"
    const val PURPOSE_DISABLE = "disable"
    const val PIN_MIN_LENGTH = 4
    const val PIN_MAX_LENGTH = 6
    const val DEFAULT_DELAY_SECONDS = 30
    const val DEBOUNCE_MS = 500L
    const val AI_SCAN_INTERVAL_MS = 3000L
    const val AI_IMAGE_SIZE = 224

    val DEFAULT_BLOCKED_KEYWORDS = listOf(
        "porn", "xxx", "xnxx", "xvideos", "pornhub",
        "xhamster", "redtube", "youporn", "brazzers",
        "onlyfans", "chaturbate", "livejasmin",
        "nude", "naked", "nsfw", "hentai",
        "sex video", "adult video", "erotic",
        "stripchat", "cam4", "bongacams",
        "escort", "hookup"
    )

    // System packages that should never be in block lists
    val SYSTEM_PROTECTED_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.launcher",
        "com.kahaf.guardian"
    )
}