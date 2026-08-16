package com.guardian.shield.service.detection

import com.guardian.shield.util.GuardianConstants
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
 * When the user crosses [REEL_SWIPE_THRESHOLD] swipes OR spends [REEL_SESSION_MS]
 * inside the app, [recordScroll] returns true so the Accessibility service can
 * show an Islamic reminder overlay.
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

    // Quran / Hadith app suggestions
    val ISLAMIC_APP_SUGGESTIONS: LinkedHashMap<String, String> = linkedMapOf(
        "com.salamweb.alquran" to "Al-Quran (Bangla)",
        "com.greentech.quran" to "iQuran",
        "com.quran.labs.androidquran" to "Quran for Android",
        "com.islamicapp.hadith" to "Hadith Collection",
        "com.ais.quran.android" to "Muslim Pro"
    )

    private val sessions = HashMap<String, ReelSession>()

    /**
     * Record a scroll event for [pkg]. Returns true when the caller should
     * surface the Islamic reminder overlay.
     *
     * @param isShortForm whether the user is currently in a "Reel/Short" view vs general feed
     */
    @Synchronized
    fun recordScroll(pkg: String, isShortForm: Boolean): Boolean {
        // We track scrolling for all non-safe apps now, but specifically prioritize REEL_PACKAGES
        val now = System.currentTimeMillis()
        pruneStaleSessions(now, pkg)
        val session = sessions.getOrPut(pkg) { ReelSession(pkg) }

        // Reset session if gap too long
        if (now - session.lastSwipeMs > GuardianConstants.SCROLL_GAP_RESET_MS && session.swipeCount > 0) {
            val fresh = ReelSession(pkg)
            sessions[pkg] = fresh
            fresh.swipeCount = 1
            fresh.lastSwipeMs = now
            return false
        }

        // Skip if reminder was shown recently (cooldown active)
        if (session.reminderShownAt > 0 && now - session.reminderShownAt < GuardianConstants.SCROLL_COOLDOWN_MS) {
            session.lastSwipeMs = now
            return false
        }

        session.swipeCount++
        session.lastSwipeMs = now

        val sessionDuration = now - session.sessionStartMs

        return if (isShortForm) {
            session.swipeCount >= GuardianConstants.REEL_SWIPE_THRESHOLD ||
                    sessionDuration >= GuardianConstants.REEL_SESSION_MS
        } else {
            // General scrolling allows more time
            sessionDuration >= GuardianConstants.GENERAL_SESSION_MS
        }
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

    /**
     * Checks if the user has been scrolling recently (within the last 2 seconds).
     */
    @Synchronized
    fun isCurrentlyScrolling(): Boolean {
        val now = System.currentTimeMillis()
        return sessions.values.any { now - it.lastSwipeMs < 2000L }
    }

    /** Evict long-idle sessions so the map cannot grow without bound. */
    private fun pruneStaleSessions(now: Long, currentPkg: String) {
        if (sessions.size <= MAX_SESSIONS) return
        val cutoff = now - SESSION_IDLE_MS
        val stale = sessions.entries
            .filter { it.key != currentPkg && now - it.value.lastSwipeMs > cutoff }
            .map { it.key }
        stale.forEach { sessions.remove(it) }
    }

    private companion object {
        const val MAX_SESSIONS = 50
        const val SESSION_IDLE_MS = 30 * 60_000L
    }
}
