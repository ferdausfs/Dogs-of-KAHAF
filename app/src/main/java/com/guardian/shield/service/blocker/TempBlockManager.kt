package com.guardian.shield.service.blocker

import com.guardian.shield.util.GuardianConstants
import timber.log.Timber
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

    /**
     * TASK 3 — Record an AI-detection event.
     *
     * Behavior:
     *  • Strikes 1..(STRIKE_THRESHOLD-1) accumulate. If [STRIKE_RESET_MS] passes
     *    without another strike, the counter resets.
     *  • Once the user hits [GuardianConstants.STRIKE_THRESHOLD] (3) strikes,
     *    apply a 24-hour hard lock ([AI_MAX_STRIKE_BLOCK_MS]) regardless of
     *    the [defaultDurationMs] caller passed in. Counter is reset so the
     *    next session starts clean.
     *
     * @param pkg the offending package
     * @param defaultDurationMs the temp-block duration to use *before* the 24h
     *        lock is triggered; kept for caller compatibility but currently
     *        unused because we always escalate to 24h on the 3rd strike.
     * @return true if a temp block was applied
     */
    @Synchronized
    fun recordAiDetection(pkg: String, defaultDurationMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val lastStrike = strikeTime[pkg] ?: 0L
        val currentStrikes = strikes[pkg] ?: 0

        // Reset the counter only if we're still below threshold AND idle too long.
        if (currentStrikes in 1 until GuardianConstants.STRIKE_THRESHOLD - 1
            && now - lastStrike > GuardianConstants.STRIKE_RESET_MS
        ) {
            strikes[pkg] = 0
        }

        val count = (strikes[pkg] ?: 0) + 1
        strikes[pkg] = count
        strikeTime[pkg] = now
        Timber.d("AI Strike $count/${GuardianConstants.STRIKE_THRESHOLD} for $pkg")

        return if (count >= GuardianConstants.STRIKE_THRESHOLD) {
            // 3rd strike → 24h hard lock (counter resets so next session is clean)
            applyTempBlock(pkg, GuardianConstants.AI_MAX_STRIKE_BLOCK_MS)
            strikes[pkg] = 0
            strikeTime.remove(pkg)
            true
        } else false
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
            strikes.remove(pkg)
            Timber.d("TempBlock expired: $pkg")
            null
        } else block
    }

    @Synchronized
    fun clearTempBlock(pkg: String) {
        tempBlocks.remove(pkg)
        strikes.remove(pkg)
        strikeTime.remove(pkg)
    }

    @Synchronized
    fun getStrikeCount(pkg: String): Int = strikes[pkg] ?: 0
}
