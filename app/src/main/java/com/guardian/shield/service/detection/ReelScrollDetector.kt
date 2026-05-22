package com.guardian.shield.service.detection

import javax.inject.Inject
import javax.inject.Singleton

/**
 * ===== TASK 2: Reel / Short scroll-addiction tracker =====
 *
 * Tracks rapid upward scrolling on apps that contain reels / shorts
 * (Instagram, YouTube Shorts, TikTok, Facebook Reels, etc.) and signals
 * the accessibility service to surface an Islamic reminder overlay
 * (`ReelReminderActivity`) when:
 *
 *  - `SWIPE_THRESHOLD` consecutive scroll events are recorded, OR
 *  - The session has been active for longer than `SESSION_DURATION_MS`.
 *
 * After a reminder is shown, the detector enters a `COOLDOWN_MS` window
 * for that package so the user is not nagged repeatedly. The host app
 * (Instagram, YouTube, etc.) is NEVER blocked — only the reel session
 * is interrupted with the reminder.
 */
data class ReelSession(
    val packageName: String,
    val sessionStartMs: Long = System.currentTimeMillis(),
    var swipeCount: Int = 0,
    var lastSwipeMs: Long = System.currentTimeMillis(),
    var reminderShownAt: Long = 0L
)

@Singleton
class ReelScrollDetector @Inject constructor() {

    /** Packages that host short-form infinite-scroll content. */
    val REEL_PACKAGES: Set<String> = setOf(
        "com.instagram.android",        // Instagram Reels
        "com.google.android.youtube",   // YouTube Shorts
        "com.zhiliaoapp.musically",     // TikTok
        "com.ss.android.ugc.trill",     // TikTok (alt region)
        "com.facebook.katana",          // Facebook Reels
        "com.snapchat.android",         // Snapchat Stories
        "com.twitter.android",          // Twitter / X (legacy)
        "com.x.android"                 // X (new package id)
    )

    /**
     * Quran / Hadith apps suggested to the user.
     * Order matters — `ReelReminderActivity` tries them in this order
     * when the user taps the "Open Quran" button.
     */
    val ISLAMIC_APP_SUGGESTIONS: LinkedHashMap<String, String> = linkedMapOf(
        "com.salamweb.alquran" to "Al-Quran (Bangla)",
        "com.greentech.quran" to "iQuran",
        "com.quran.labs.androidquran" to "Quran for Android",
        "com.islamicapp.hadith" to "Hadith Collection",
        "com.ais.quran.android" to "Muslim Pro"
    )

    private val sessions = HashMap<String, ReelSession>()

    companion object {
        /** Swipes before a reminder is triggered. */
        const val SWIPE_THRESHOLD = 15

        /** Total reel-session duration before a reminder is triggered. */
        const val SESSION_DURATION_MS = 5 * 60_000L  // 5 minutes

        /** Cooldown window after a reminder is shown, per package. */
        const val COOLDOWN_MS = 30 * 60_000L         // 30 minutes

        /** Gap between scrolls large enough to reset the session. */
        const val SWIPE_GAP_RESET_MS = 30_000L       // 30 seconds
    }

    /**
     * Record a scroll event for [pkg].
     *
     * @return true if the caller should now show the reel reminder overlay.
     */
    @Synchronized
    fun recordScroll(pkg: String): Boolean {
        if (!REEL_PACKAGES.contains(pkg)) return false
        val now = System.currentTimeMillis()
        val session = sessions.getOrPut(pkg) { ReelSession(pkg) }

        // Big gap between scrolls => start a fresh session.
        if (now - session.lastSwipeMs > SWIPE_GAP_RESET_MS) {
            sessions[pkg] = ReelSession(pkg)
            return false
        }

        // Within cooldown window => don't nag, but keep counting silently.
        if (session.reminderShownAt > 0 && now - session.reminderShownAt < COOLDOWN_MS) {
            session.lastSwipeMs = now
            return false
        }

        session.swipeCount++
        session.lastSwipeMs = now

        val sessionDuration = now - session.sessionStartMs
        return session.swipeCount >= SWIPE_THRESHOLD ||
                sessionDuration >= SESSION_DURATION_MS
    }

    /** Mark that the reminder has just been shown for [pkg]. */
    @Synchronized
    fun markReminderShown(pkg: String) {
        val s = sessions[pkg] ?: ReelSession(pkg).also { sessions[pkg] = it }
        s.reminderShownAt = System.currentTimeMillis()
        s.swipeCount = 0
    }

    /** Clear any tracked session for [pkg] (e.g. when the user leaves the app). */
    @Synchronized
    fun resetSession(pkg: String) {
        sessions.remove(pkg)
    }
}
