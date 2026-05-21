package com.guardianshield.app.manager

import com.guardianshield.app.util.Constants

/**
 * v2: 3 strikes → 24-hour fixed block.
 * - Strikes do NOT reset on a daily timer.
 * - Strikes reset ONLY when an active block expires.
 * - During an active block, new AI detections are ignored.
 *
 * Thread-safe: all public methods are @Synchronized so the AccessibilityService
 * and coroutines can call concurrently.
 */
object TempBlockManager {

    data class TempBlock(
        val packageName: String,
        val until: Long,
        val strikeCount: Int
    )

    private val strikes: MutableMap<String, Int> = mutableMapOf()
    private val blocks: MutableMap<String, TempBlock> = mutableMapOf()

    /**
     * Record an AI detection event.
     *
     * @return strike count after recording (1, 2, or 3). Returns -1 if package
     *   is already inside an active block (event ignored).
     */
    @Synchronized
    fun recordAiDetection(pkg: String): Int {
        // If already blocked, ignore.
        val existing = blocks[pkg]
        if (existing != null && System.currentTimeMillis() < existing.until) {
            return -1
        }
        // If block expired, clean it.
        if (existing != null) {
            blocks.remove(pkg)
            strikes.remove(pkg)
        }

        val current = (strikes[pkg] ?: 0) + 1
        strikes[pkg] = current

        if (current >= Constants.STRIKE_THRESHOLD) {
            val until = System.currentTimeMillis() + Constants.AI_BLOCK_DURATION_MS
            blocks[pkg] = TempBlock(pkg, until, current)
        }
        return current
    }

    /** @return active TempBlock for [pkg] or null. Cleans up expired entries. */
    @Synchronized
    fun isTempBlocked(pkg: String): TempBlock? {
        val b = blocks[pkg] ?: return null
        return if (System.currentTimeMillis() < b.until) b
        else {
            // Block expired → reset strikes for this package.
            blocks.remove(pkg)
            strikes.remove(pkg)
            null
        }
    }

    @Synchronized
    fun currentStrikes(pkg: String): Int = strikes[pkg] ?: 0

    /** Manual reset (admin / PIN required). */
    @Synchronized
    fun resetStrikes(pkg: String) {
        strikes.remove(pkg)
        blocks.remove(pkg)
    }

    @Synchronized
    fun resetAll() {
        strikes.clear()
        blocks.clear()
    }

    @Synchronized
    fun snapshot(): Map<String, TempBlock> = HashMap(blocks)
}
