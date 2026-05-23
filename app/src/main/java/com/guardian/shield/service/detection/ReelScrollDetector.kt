package com.guardian.shield.service.detection

import javax.inject.Inject
import javax.inject.Singleton

data class ReelSession(
    val packageName: String,
    val sessionStartMs: Long = System.currentTimeMillis(),
    var swipeCount: Int = 0,
    var lastSwipeMs: Long = System.currentTimeMillis(),
    var reminderShownAt: Long = 0L
)

/**
 * TASK 2 — Reel/Short scroll addiction detector.
 *
 * Tracks consecutive scroll events in known short-form video apps.
 * When the user crosses [SWIPE_THRESHOLD] swipes OR spends [SESSION_DURATION_MS]
 * inside the app, [recordScroll] returns true so the Accessibility service can
 * show an Islamic reminder overlay. After a reminder is shown, [COOLDOWN_MS]
 * suppresses any further reminder for that package to avoid nagging.
 */
@Singleton
class ReelScrollDetector @Inject constructor() {

    // Packages that have reel / shorts content
    val REEL_PACKAGES: Set<String> = setOf(
        "com.instagram.android",        // Instagram Reels
        "com.google.android.youtube",   // YouTube Shorts
        "com.zhiliaoapp.musically",     // TikTok
        "com.ss.android.ugc.trill",     // TikTok alt
        "com.facebook.katana",          // Facebook Reels
        "com.snapchat.android",         // Snapchat Stories
        "com.twitter.android",          // Twitter / X (old)
        "com.x.android"                 // X (new package)
    )

    // Quran / Hadith app suggestions (package -> display)
    val ISLAMIC_APP_SUGGESTIONS: LinkedHashMap<String, String> = linkedMapOf(
        "com.salamweb.alquran" to "Al-Quran (Bangla)",
        "com.greentech.quran" to "iQuran",
        "com.quran.labs.androidquran" to "Quran for Android",
        "com.islamicapp.hadith" to "Hadith Collection",
        "com.ais.quran.android" to "Muslim Pro"
    )

    private val sessions = HashMap<String, ReelSession>()

    companion object {
        const val SWIPE_THRESHOLD = 15              // swipes before reminder
        const val SESSION_DURATION_MS = 5 * 60_000L // 5 minutes
        const val COOLDOWN_MS = 30 * 60_000L        // 30 min cooldown after reminder
        const val SWIPE_GAP_RESET_MS = 30_000L      // 30s gap resets session
    }

    /**
     * Record a scroll event for [pkg]. Returns true when the caller should
     * surface the Islamic reminder overlay.
     */
    @Synchronized
    fun recordScroll(pkg: String): Boolean {
        if (!REEL_PACKAGES.contains(pkg)) return false
        val now = System.currentTimeMillis()
        val session = sessions.getOrPut(pkg) { ReelSession(pkg) }

        // Reset session if gap too long
        if (now - session.lastSwipeMs > SWIPE_GAP_RESET_MS && session.swipeCount > 0) {
            val fresh = ReelSession(pkg)
            sessions[pkg] = fresh
            fresh.swipeCount = 1
            fresh.lastSwipeMs = now
            return false
        }

        // Skip if reminder was shown recently (cooldown active)
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

    @Synchronized
    fun markReminderShown(pkg: String) {
        val s = sessions[pkg] ?: return
        s.reminderShownAt = System.currentTimeMillis()
        s.swipeCount = 0
    }

    @Synchronized
    fun resetSession(pkg: String) {
        sessions.remove(pkg)
    }
}
