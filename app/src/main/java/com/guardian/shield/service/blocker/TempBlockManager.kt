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
     * AI detection event record করো।
     *
     * ===== TASK 3: 3 strikes -> 24h hard lock =====
     * - AI detection can block an app a maximum of 3 times.
     * - On the 3rd strike, a 24-hour hard lock is applied (NOT the caller's
     *   `defaultDurationMs`). The 24h window is measured from the moment of
     *   the 3rd block.
     * - "Daily 3 strikes" is NOT the intent — it is lifetime 3 strikes per
     *   session. The counter only resets after the 24h lock expires (the
     *   existing `isTempBlocked()` expiry path clears strikes), or after
     *   a large gap (STRIKE_RESET_MS) before reaching the threshold.
     *
     * @param defaultDurationMs Ignored once the threshold is reached; kept
     *   for backwards compatibility with the existing call site.
     * @return true if a temp block was applied on this call.
     */
    @Synchronized
    fun recordAiDetection(pkg: String, defaultDurationMs: Long): Boolean {
        // Reset old strikes only BEFORE the threshold is reached and only
        // when the gap is large enough. Don't reset at the threshold itself.
        val lastStrike = strikeTime[pkg] ?: 0L
        val currentStrikes = strikes[pkg] ?: 0
        if (currentStrikes < GuardianConstants.STRIKE_THRESHOLD - 1
            && System.currentTimeMillis() - lastStrike > GuardianConstants.STRIKE_RESET_MS
        ) {
            strikes[pkg] = 0
        }

        val count = (strikes[pkg] ?: 0) + 1
        strikes[pkg] = count
        strikeTime[pkg] = System.currentTimeMillis()
        Timber.d("AI Strike $count/${GuardianConstants.STRIKE_THRESHOLD} for $pkg")

        return if (count >= GuardianConstants.STRIKE_THRESHOLD) {
            // 3rd strike -> 24h hard lock (overrides defaultDurationMs)
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