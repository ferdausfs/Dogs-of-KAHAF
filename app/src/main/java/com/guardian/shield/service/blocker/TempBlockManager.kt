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

    /**
     * Duplicate detection within the short burst window
     * ([GuardianConstants.STRIKE_BURST_DEDUP_MS]) — no user-visible action.
     */
    data object Duplicate : AiStrikeResult()

    /**
     * The strike-1/2 warning card is currently on screen and unacknowledged.
     * The next strike must not be counted until the user dismisses it (or the
     * safety fallback fires). Distinct from [Duplicate] so logs can tell the
     * two gates apart.
     */
    data object WarningCardVisible : AiStrikeResult()

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

    // v3.3.0 Bug-A redesign: the strike-1/2 warning card is user-dismissed, so
    // the inter-strike gate is this explicit flag — NOT a fixed 3.5s window.
    // Set true when the card is shown; cleared when the user acknowledges /
    // dismisses it (or the safety fallback fires).
    @Volatile
    private var warningCardShowing: Boolean = false

    // TASK A — confidence score from the most recent AI detection that triggered
    // a strike. Read by the "Not sensitive" / "Mark False" handler to decide
    // whether to apply immediately (LOW confidence) or queue for cooling-off
    // (HIGH confidence). Set by recordAiDetection(); -1f = no score available.
    @Volatile
    var lastStrikeConfidence: Float = -1f
        private set

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
     *  • While the strike-1/2 warning card is on screen ([isWarningCardShowing])
     *    a new detection returns [AiStrikeResult.WarningCardVisible] — no strike
     *    counted. The gate reopens when the user acknowledges / dismisses the
     *    card (or the [GuardianConstants.STRIKE_WARNING_SAFETY_FALLBACK_MS]
     *    safety net fires). This replaced the old 3.5s fixed-duration gate
     *    (Bug A) once dismiss became user-driven.
     *  • A second detection within [GuardianConstants.STRIKE_BURST_DEDUP_MS]
     *    of the previous strike returns [AiStrikeResult.Duplicate] (same-tick
     *    concurrent-scan protection — independent of the card-visibility flag).
     *
     * @param pkg the offending package
     * @param blockDurationMs the user-configured temp-block duration
     * @param confidence the AI detection confidence score (0..1) from the model.
     *        -1f if unavailable. Stored in [lastStrikeConfidence] for the overlay
     *        and the cooling-off gate to read.
     */
    @Synchronized
    fun recordAiDetection(pkg: String, blockDurationMs: Long, confidence: Float = -1f): AiStrikeResult {
        val now = System.currentTimeMillis()
        // TASK A — persist the confidence for the overlay badge and the
        // confidence-based cooling-off gate.
        lastStrikeConfidence = confidence

        // Grace period after a block expiry — don't count strikes or block.
        if (now < (graceUntil[pkg] ?: 0L)) {
            Timber.d("Grace period active for $pkg — AI detection ignored")
            return AiStrikeResult.GracePeriod
        }

        // Don't count a new strike while the previous warning card is still
        // visible / unacknowledged. Explicit state, not a fixed duration
        // (v3.3.0 redesign of Bug A).
        if (warningCardShowing) {
            Timber.d("Ignoring AI strike for $pkg — warning card still showing/unacknowledged")
            return AiStrikeResult.WarningCardVisible
        }

        val lastStrike = strikeTime[pkg] ?: 0L

        // Short burst dedup: two concurrent scan paths (content-aware region +
        // full-frame) must not increment twice in the same tick after the card
        // has been dismissed.
        if (now - lastStrike < GuardianConstants.STRIKE_BURST_DEDUP_MS) {
            Timber.d("Ignoring duplicate AI strike for $pkg (burst dedup ${GuardianConstants.STRIKE_BURST_DEDUP_MS}ms)")
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
            // but do NOT show a block overlay. Raise the visibility gate
            // immediately so a scan arriving before the card is attached
            // cannot become strike N+1.
            warningCardShowing = true
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

    /**
     * Undo the most recent strike for [pkg] (Bug B — the strike-1/2 warning card's
     * "Not sensitive" report). Decrements the live strike counter by one (floor 0)
     * and clears the inter-strike timestamp so the burst-dedup window does not
     * impose a "waiting" penalty on the user's next legitimate scan. The
     * warning-card visibility flag is owned by the overlay (set/cleared from
     * GuardianAccessibilityService) — this method does not touch it.
     *
     * This is a per-event undo ONLY — it never calls
     * [com.guardian.shield.service.detection.FalsePositiveMemory.addSignature] and
     * never whitelists any visual pattern, so future detections are evaluated
     * normally by AiDetector exactly as before.
     */
    @Synchronized
    fun cancelLastStrike(pkg: String) {
        val before = strikes[pkg] ?: 0
        val after = (before - 1).coerceAtLeast(0)
        if (after == 0) strikes.remove(pkg) else strikes[pkg] = after
        // Rewind the dedup timestamp so a reported-safe event doesn't eat into the
        // next legitimate strike's allowed window (no "waiting" penalty).
        strikeTime.remove(pkg)
        Timber.d("cancelLastStrike($pkg): strike count $before -> $after (timestamp cleared)")
    }

    /**
     * Mark the strike-1/2 warning card as currently on screen (or not).
     * While true, [recordAiDetection] refuses to count another strike.
     */
    @Synchronized
    fun setWarningCardShowing(showing: Boolean) {
        warningCardShowing = showing
        Timber.d("warningCardShowing=$showing")
    }

    @Synchronized
    fun isWarningCardShowing(): Boolean = warningCardShowing
}
