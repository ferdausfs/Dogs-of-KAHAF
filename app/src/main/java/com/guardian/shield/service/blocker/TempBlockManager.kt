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
    // Ceil to the next whole minute so a 15 min block shows "15", not "16",
    // while 1 ms remaining still shows 1 instead of 0.
    val remainingMinutes get() =
        if (remainingMs <= 0L) 0L else (remainingMs + 59_999L) / 60_000L
}

/** Result of recording an AI detection. */
sealed class AiStrikeResult {
    /**
     * A strike was counted but the user hasn't hit the block threshold yet.
     * [strikeCount] is the 1-based current strike number (1..threshold-1) so the
     * caller can surface a visible warning ("strike N of 3") BEFORE the block,
     * instead of leaving strikes 1-2 completely silent.
     */
    data class StrikeCounted(val strikeCount: Int) : AiStrikeResult()

    /** Duplicate detection within the 1-second dedup window — no user-visible action. */
    data object Duplicate : AiStrikeResult()

    /** A recent temp block just expired; the user gets a grace period. */
    data object GracePeriod : AiStrikeResult()

    /** Block threshold reached — a temp block was applied. */
    data class Blocked(val detail: String) : AiStrikeResult()
}

@Singleton
class TempBlockManager @Inject constructor() {

    private val strikes = LinkedHashMap<String, Int>()
    private val strikeTime = LinkedHashMap<String, Long>()
    private val tempBlocks = LinkedHashMap<String, TempBlock>()

    // Tracks history of blocks to handle escalation (3 blocks in 2 hours)
    private val blockHistory = LinkedHashMap<String, LinkedList<Long>>()

    // POST-BLOCK GRACE — right after a temp block expires we let the user back
    // in without instantly re-blocking. Without this, the app would re-block on
    // the first AI scan after expiry, so a "15 min block" felt like it never
    // unblocked. During the grace window, AI detection is ignored for the pkg.
    private val graceUntil = HashMap<String, Long>()

    /**
     * TASK 3 — Record an AI-detection event.
     *
     * Behavior (now matches the README and the user's setting):
     *  • Strikes 1..(STRIKE_THRESHOLD-1) return [AiStrikeResult.StrikeCounted]
     *    carrying the 1-based strike number (no block shown). The caller uses
     *    [AiStrikeResult.StrikeCounted.strikeCount] to show a visible warning
     *    Toast ("strike N of 3") so the eventual block never feels unannounced.
     *  • On the 3rd strike a temp block is applied for [blockDurationMs] — this
     *    is the user-configured duration from Settings (default 15 min), which
     *    was previously ignored.
     *  • If blocked 3 times within 2 hours, escalate to a 24-hour lock.
     *  • If a temp block just expired, return [AiStrikeResult.GracePeriod] so the
     *    app stays unlocked for [GuardianConstants.POST_BLOCK_GRACE_MS].
     *  • A second detection within 1s of the previous strike returns
     *    [AiStrikeResult.Duplicate] (dedup — no strike counted, no warning).
     *
     * @param pkg the offending package
     * @param blockDurationMs the user-configured temp-block duration
     */
    @Synchronized
    fun recordAiDetection(pkg: String, blockDurationMs: Long): AiStrikeResult {
        val now = System.currentTimeMillis()

        // Grace period after a block expiry — don't count strikes or block.
        if (now < (graceUntil[pkg] ?: 0L)) {
            Timber.d("Grace period active for $pkg — AI detection ignored")
            return AiStrikeResult.GracePeriod
        }

        val lastStrike = strikeTime[pkg] ?: 0L

        // Prevent multiple strikes within 1 second for the same package
        if (now - lastStrike < 1000L) {
            Timber.d("Ignoring duplicate AI strike for $pkg (too soon)")
            return AiStrikeResult.Duplicate
        }

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

            val detail = handleBlockEscalation(pkg, blockDurationMs)
            AiStrikeResult.Blocked(detail)
        } else {
            // Below threshold — strike counted (carry the number for a warning),
            // but do NOT show a block overlay.
            AiStrikeResult.StrikeCounted(count)
        }
    }

    private fun handleBlockEscalation(pkg: String, blockDurationMs: Long): String {
        val now = System.currentTimeMillis()
        val history = blockHistory.getOrPut(pkg) { LinkedList() }

        // Remove blocks older than the escalation window
        while (history.isNotEmpty() && now - history.first > GuardianConstants.ESCALATION_WINDOW_MS) {
            history.removeFirst()
        }

        history.add(now)

        // AI-originated temp blocks carry an ";ai" marker so the overlay can tell
        // them apart from a plain app-block enforcement (reason=APP_BLOCKED) and
        // only offer "this was a false block" when there is actually a remembered
        // AI-frame candidate to learn from.
        return if (history.size >= GuardianConstants.ESCALATION_THRESHOLD) {
            // Escalation triggered: Block for the day
            Timber.w("Escalation triggered for $pkg: 3 blocks in 2 hours. Blocking for 24h.")
            applyTempBlock(pkg, GuardianConstants.DAY_BLOCK_MS)
            history.clear() // Reset history after escalation
            "temp_block:${GuardianConstants.DAY_BLOCK_MS / 60_000}min;ai"
        } else {
            // Normal block for the user-configured duration (default 15 min)
            val mins = (blockDurationMs / 60_000).coerceAtLeast(1)
            applyTempBlock(pkg, blockDurationMs)
            "temp_block:${mins}min;ai"
        }
    }

    @Synchronized
    fun applyTempBlock(pkg: String, durationMs: Long) {
        tempBlocks[pkg] = TempBlock(pkg, System.currentTimeMillis(), durationMs)
        // End any earlier grace so a fresh block is respected.
        graceUntil.remove(pkg)
        Timber.w("TempBlock: $pkg for ${durationMs / 60_000} min")
    }

    @Synchronized
    fun isTempBlocked(pkg: String): TempBlock? {
        val block = tempBlocks[pkg] ?: return null
        return if (block.isExpired) {
            tempBlocks.remove(pkg)
            // Grant a grace window so the app genuinely unblocks after the
            // configured duration instead of being re-blocked immediately.
            graceUntil[pkg] = System.currentTimeMillis() + GuardianConstants.POST_BLOCK_GRACE_MS
            Timber.d("TempBlock expired: $pkg — grace period until ${graceUntil[pkg]}")
            null
        } else block
    }

    /** True when a recent temp block just expired and AI re-blocking is paused. */
    @Synchronized
    fun isGracePeriodActive(pkg: String): Boolean =
        System.currentTimeMillis() < (graceUntil[pkg] ?: 0L)

    @Synchronized
    fun clearTempBlock(pkg: String) {
        tempBlocks.remove(pkg)
        strikes.remove(pkg)
        strikeTime.remove(pkg)
        blockHistory.remove(pkg)
        graceUntil.remove(pkg)
    }

    @Synchronized
    fun getStrikeCount(pkg: String): Int = strikes[pkg] ?: 0
}
