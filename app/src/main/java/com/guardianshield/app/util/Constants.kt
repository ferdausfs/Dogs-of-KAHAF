package com.guardianshield.app.util

object Constants {
    // ---- Notification channels ----
    const val CHANNEL_PROTECTION = "guardian_protection"
    const val CHANNEL_ALERTS = "guardian_alerts"
    const val CHANNEL_REMINDERS = "guardian_reminders"

    const val NOTIF_ID_PROTECTION = 1001
    const val NOTIF_ID_TAMPER = 1002
    const val NOTIF_ID_BLOCK = 1003

    // ---- AI Detection: 3 strikes → 24h block ----
    const val STRIKE_THRESHOLD = 3
    const val AI_BLOCK_DURATION_MS: Long = 24L * 60L * 60L * 1_000L // 24h fixed

    // ---- Scroll Addiction ----
    const val SCROLL_WINDOW_MS: Long = 60_000L           // 1 minute
    const val SCROLL_THRESHOLD_DEFAULT = 15              // 15 swipes / min
    const val SCROLL_COOLDOWN_MS_DEFAULT: Long = 5L * 60L * 1_000L // 5 min
    const val SCROLL_SKIP_COUNTDOWN_SEC = 30

    // ---- Short video / reel packages monitored ----
    val SHORT_VIDEO_PACKAGES = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",   // TikTok
        "com.ss.android.ugc.trill",   // TikTok (older id)
        "com.facebook.katana",
        "com.snapchat.android",
        "com.twitter.android",
        "com.reddit.frontpage"
    )

    // ---- Default Allowlist (communication + keyboards) ----
    val DEFAULT_ALLOWLIST_PACKAGES = setOf(
        // IMO
        "com.imo.android.imoim",
        "com.imo.android.imoim2",
        "com.imo.android.imoimbeta",
        // Signal
        "org.thoughtcrime.securesms",
        // Messenger
        "com.facebook.orca",
        "com.facebook.mlite",
        // Other comms
        "com.viber.voip",
        "jp.naver.line.android",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "com.telegram.messenger",
        "com.whatsapp",
        "com.whatsapp.w4b",
        // Keyboards — international
        "com.google.android.inputmethod.latin",   // Gboard
        "com.touchtype.swiftkey",
        "com.microsoft.swiftkey",
        "com.samsung.android.honeyboard",
        "com.grammarly.android.keyboard",
        // Keyboards — Bangla
        "com.bettermorrow.ridmik",
        "com.mayabi.phonebangla",
        "com.omicronlab.avro",
        "bangla.keyboard.bijoy.phonetic",
        "com.linix.banglakeyboard",
        // Dialer / system essentials
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer"
    )

    // ---- Known Quran apps (for "Open Quran" fallback) ----
    val QURAN_PACKAGES = listOf(
        "com.quran.labs.androidquran",
        "com.muslimpro.app",
        "com.greentech.quran",
        "com.nerdybits.quranicaudio",
        "com.bitsmedia.android.muslimpro",
        "com.pakdata.QuranMajeed"
    )

    // ---- Preference keys ----
    const val PREF_FILE = "guardian_secure_prefs"
    const val PREF_PIN_HASH = "pin_hash"
    const val PREF_PIN_SALT = "pin_salt"
    const val PREF_STEALTH_MODE = "stealth_mode"
    const val PREF_SCROLL_ENABLED = "scroll_enabled"
    const val PREF_SCROLL_THRESHOLD = "scroll_threshold"
    const val PREF_SCROLL_COOLDOWN_MIN = "scroll_cooldown_min"
    const val PREF_AI_ENABLED = "ai_enabled"
    const val PREF_DEFAULT_ALLOWLIST_INIT = "default_allowlist_init"
    const val PREF_PARENT_PHONE = "parent_phone"
    const val PREF_PARENT_EMAIL = "parent_email"

    // ---- Action / Extra keys ----
    const val EXTRA_BLOCK_PACKAGE = "extra_block_package"
    const val EXTRA_BLOCK_REASON = "extra_block_reason"
    const val EXTRA_BLOCK_DURATION = "extra_block_duration"
    const val EXTRA_STRIKE_COUNT = "extra_strike_count"

    // ---- Block reasons ----
    const val REASON_BLOCKLIST = "blocklist"
    const val REASON_SCHEDULE = "schedule"
    const val REASON_AI = "ai_detection"
    const val REASON_KEYWORD = "keyword"
}
