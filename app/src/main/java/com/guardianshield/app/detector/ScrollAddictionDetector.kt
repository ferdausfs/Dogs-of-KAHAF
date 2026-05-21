package com.guardianshield.app.detector

import android.util.Log
import com.guardianshield.app.util.Constants

/**
 * v2 Feature 2 — Scroll Addiction Detection.
 *
 * Counts upward scroll events per package within a sliding 1-minute window.
 * When count >= threshold within window → caller is notified to show
 * the Quran/Hadith suggestion overlay. Then a cooldown begins so the
 * suggestion does not appear repeatedly.
 *
 * Memory safe: tracks at most 10 packages (oldest evicted FIFO).
 */
class ScrollAddictionDetector(
    var threshold: Int = Constants.SCROLL_THRESHOLD_DEFAULT,
    var windowMs: Long = Constants.SCROLL_WINDOW_MS,
    var cooldownMs: Long = Constants.SCROLL_COOLDOWN_MS_DEFAULT
) {
    private data class Counter(val timestamps: ArrayDeque<Long> = ArrayDeque())

    private val scrollCounts: LinkedHashMap<String, Counter> = LinkedHashMap()
    private val lastShownAt: MutableMap<String, Long> = mutableMapOf()

    /**
     * Record a scroll event for [pkg].
     * @return true if the overlay should be shown now.
     */
    @Synchronized
    fun recordScroll(pkg: String): Boolean {
        if (pkg !in Constants.SHORT_VIDEO_PACKAGES) return false

        val now = System.currentTimeMillis()

        // Cooldown: skip if recently shown.
        val last = lastShownAt[pkg]
        if (last != null && now - last < cooldownMs) return false

        val counter = scrollCounts.getOrPut(pkg) { Counter() }
        counter.timestamps.addLast(now)

        // Prune timestamps outside the window.
        val cutoff = now - windowMs
        while (counter.timestamps.isNotEmpty() && counter.timestamps.first() < cutoff) {
            counter.timestamps.removeFirst()
        }

        // Evict oldest if memory map grew too large.
        if (scrollCounts.size > 10) {
            val iter = scrollCounts.entries.iterator()
            if (iter.hasNext()) { iter.next(); iter.remove() }
        }

        if (counter.timestamps.size >= threshold) {
            lastShownAt[pkg] = now
            counter.timestamps.clear()
            Log.d(TAG, "Scroll addiction triggered for $pkg")
            return true
        }
        return false
    }

    @Synchronized
    fun reset(pkg: String? = null) {
        if (pkg == null) {
            scrollCounts.clear()
            lastShownAt.clear()
        } else {
            scrollCounts.remove(pkg)
            lastShownAt.remove(pkg)
        }
    }

    companion object { private const val TAG = "ScrollDetector" }
}
