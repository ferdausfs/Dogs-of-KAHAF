package com.guardian.shield.util

import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import java.util.Calendar

/**
 * PHASE 3 (v3.5.0) — clean-streak + weekly comparison for the Home dashboard.
 *
 * REUSES the existing block_events table (zero duplicate logging). Verified
 * against BlockingEngine.block()/logEvent():
 *  - A "strike-3 full block" row is `reason = AI_DETECTION` AND
 *    `matchedTerm LIKE "temp_block:%"` — those rows are only written when
 *    the 3rd strike applies a temp block (strike-1/2 warning cards never
 *    reach logEvent; the caller returns early instead).
 *  - `NOT_SENSITIVE` rows are user reports, not blocks — excluded from
 *    everything here.
 *  - The weekly comparison counts ALL other block events (AI full blocks,
 *    app/schedule/keyword/tamper blocks) over trailing 7-day windows.
 *
 * Streak definition: number of consecutive local calendar days, counting
 * back from today (inclusive: a today with no full block so far still
 * counts), with zero strike-3 full blocks. One full block breaks a day.
 *
 * Pure + offline: takes the event list as input so it is trivially testable
 * and adds no DAO of its own.
 */
object StreakCalculator {

    private const val TEMP_BLOCK_PREFIX = "temp_block:"

    data class StreakInfo(
        val streakDays: Int = 0,
        val thisWeekBlocks: Int = 0,
        val lastWeekBlocks: Int = 0,
        /** % change vs last week; positive = more blocks. null when last week == 0 (no baseline). */
        val deltaPct: Int? = null,
        /** False until any block event exists at all — lets the UI greet newcomers. */
        val hasAnyBlocks: Boolean = false,
        /** True iff today itself contains a strike-3 full block (streak broke today). */
        val fullBlockToday: Boolean = false
    )

    /**
     * @param floorDayStart local midnight of the earliest day that can count
     *   toward the streak (pass app first-install day). Without a floor a
     *   streak would be unbounded for users with zero full blocks ever; with
     *   the install floor a newcomer correctly reads "days since install".
     */
    fun compute(
        events: List<BlockEvent>,
        now: Long = System.currentTimeMillis(),
        floorDayStart: Long = Long.MIN_VALUE
    ): StreakInfo {
        val blocks = events.filter { it.reason != BlockReason.NOT_SENSITIVE }

        // --- weekly windows (trailing 7d vs previous 7d) ---
        val weekMs = 7L * 24 * 60 * 60 * 1_000L
        val thisWeekStart = now - weekMs
        val thisWeek = blocks.count { it.timestamp >= thisWeekStart }
        val lastWeek = blocks.count { it.timestamp < thisWeekStart }
        val deltaPct = if (lastWeek == 0) null else ((thisWeek - lastWeek) * 100) / lastWeek

        // --- streak over strike-3 AI full blocks, by local day ---
        val todayStart = localDayStart(now)
        val blockDays = HashSet<Long>()
        var fullBlockToday = false
        for (e in blocks) {
            if (e.reason == BlockReason.AI_DETECTION &&
                (e.matchedTerm?.startsWith(TEMP_BLOCK_PREFIX) == true)
            ) {
                val day = localDayStart(e.timestamp)
                blockDays.add(day)
                if (day == todayStart) fullBlockToday = true
            }
        }
        var streak = 0
        var cursor = todayStart
        while (cursor >= floorDayStart && !blockDays.contains(cursor)) {
            streak++
            // Calendar arithmetic (not fixed 24h) so the streak survives
            // DST-length days on any device locale.
            cursor = Calendar.getInstance().apply {
                timeInMillis = cursor
                add(Calendar.DAY_OF_YEAR, -1)
            }.timeInMillis
            if (streak > 10_000) break   // absolute paranoia bound
        }

        return StreakInfo(
            streakDays = streak,
            thisWeekBlocks = thisWeek,
            lastWeekBlocks = lastWeek,
            deltaPct = deltaPct,
            hasAnyBlocks = blocks.isNotEmpty(),
            fullBlockToday = fullBlockToday
        )
    }

    /** Local midnight (00:00:00.000) containing [time]. */
    fun localDayStart(time: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
