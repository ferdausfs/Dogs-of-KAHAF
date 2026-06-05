package com.guardian.shield.service.blocker

import com.guardian.shield.util.GuardianConstants
import timber.log.Timber
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

data class TempBlock(
    val packageName: String,
    val blockedAt: Long,
    val durationMs: Long
) {
    val expiresAt get() = blockedAt + durationMs
    val isExpired get() = System.currentTimeMillis() > expiresAt
    val remainingMs get() = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
    val remainingMinutes get() = (remainingMs / 60_000) + 1
}

@Singleton
class TempBlockManager @Inject constructor() {

    private val strikes = LinkedHashMap<String, Int>()
    private val strikeTime = LinkedHashMap<String, Long>()
    private val tempBlocks = LinkedHashMap<String, TempBlock>()

    // Tracks history of blocks to handle escalation (3 blocks in 2 hours)
    private val blockHistory = LinkedHashMap<String, LinkedList<Long>>()

    /**
     * TASK 3 — Record an AI-detection event.
     *
     * Behavior:
     *  • Strikes 1..(STRIKE_THRESHOLD-1) accumulate.
     *  • Once the user hits [GuardianConstants.STRIKE_THRESHOLD] (3) strikes,
     *    apply a 15-minute block.
     *  • If blocked 3 times within 2 hours, apply a 24-hour lock.
     *
     * @param pkg the offending package
     * @param defaultDurationMs the temp-block duration (ignored in favor of new logic)
     * @return true if a temp block was applied
     */
    @Synchronized
    fun recordAiDetection(pkg: String, defaultDurationMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val lastStrike = strikeTime[pkg] ?: 0L
        val currentStrikes = strikes[pkg] ?: 0

        // Reset the counter only if we're still below threshold AND idle too long.
        if (currentStrikes in 1 until GuardianConstants.STRIKE_THRESHOLD
            && now - lastStrike > GuardianConstants.STRIKE_RESET_MS
        ) {
            strikes[pkg] = 0
        }

        val count = (strikes[pkg] ?: 0) + 1
        strikes[pkg] = count
        strikeTime[pkg] = now
        Timber.d("AI Strike $count/${GuardianConstants.STRIKE_THRESHOLD} for $pkg")

        return if (count >= GuardianConstants.STRIKE_THRESHOLD) {
            // 3rd strike → Trigger a block
            strikes[pkg] = 0
            strikeTime.remove(pkg)

            handleBlockEscalation(pkg)
            true
        } else false
    }

    private fun handleBlockEscalation(pkg: String) {
        val now = System.currentTimeMillis()
        val history = blockHistory.getOrPut(pkg) { LinkedList() }

        // Remove blocks older than the escalation window
        while (history.isNotEmpty() && now - history.first > GuardianConstants.ESCALATION_WINDOW_MS) {
            history.removeFirst()
        }

        history.add(now)

        if (history.size >= GuardianConstants.ESCALATION_THRESHOLD) {
            // Escalation triggered: Block for the day
            Timber.w("Escalation triggered for $pkg: 3 blocks in 2 hours. Blocking for 24h.")
            applyTempBlock(pkg, GuardianConstants.DAY_BLOCK_MS)
            history.clear() // Reset history after escalation
        } else {
            // Normal 15-minute block
            applyTempBlock(pkg, GuardianConstants.DEFAULT_TEMP_BLOCK_MS)
        }
    }

    @Synchronized
    fun applyTempBlock(pkg: String, durationMs: Long) {
        tempBlocks[pkg] = TempBlock(pkg, System.currentTimeMillis(), durationMs)
        Timber.w("TempBlock: $pkg for ${durationMs / 60_000} min")
    }

    @Synchronized
    fun isTempBlocked(pkg: String): TempBlock? {
        val block = tempBlocks[pkg] ?: return null
        return if (block.isExpired) {
            tempBlocks.remove(pkg)
            // Note: we don't necessarily want to remove strikes here if they are in progress
            Timber.d("TempBlock expired: $pkg")
            null
        } else block
    }

    @Synchronized
    fun clearTempBlock(pkg: String) {
        tempBlocks.remove(pkg)
        strikes.remove(pkg)
        strikeTime.remove(pkg)
        blockHistory.remove(pkg)
    }

    @Synchronized
    fun getStrikeCount(pkg: String): Int = strikes[pkg] ?: 0
}
