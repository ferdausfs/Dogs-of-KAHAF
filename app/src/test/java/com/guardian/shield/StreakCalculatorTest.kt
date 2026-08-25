package com.guardian.shield

import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.util.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * R11 (v3.8.1) — unit tests for the dashboard streak / weekly stats math.
 */
class StreakCalculatorTest {

    private val now: Long = System.currentTimeMillis()

    private fun ev(
        reason: BlockReason,
        term: String?,
        timestamp: Long,
        id: Long = 0L
    ) = BlockEvent(id, "com.example.app", reason, term, timestamp)

    private fun fullBlock(timestamp: Long) = ev(BlockReason.AI_DETECTION, "temp_block:x", timestamp)

    private fun daysAgoMidnight(days: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // ------------------------------------------------------------- streak

    @Test
    fun `no events counts every day since the install floor, inclusive`() {
        val floor = daysAgoMidnight(2) // installed 2 days ago
        val info = StreakCalculator.compute(emptyList(), now, floor)
        assertEquals(3, info.streakDays) // floor-day + yesterday + today
        assertFalse(info.hasAnyBlocks)
        assertFalse(info.fullBlockToday)
    }

    @Test
    fun `a full block yesterday stops the streak at today only`() {
        val yesterdayNoon = daysAgoMidnight(1) + 12L * 60 * 60 * 1000L
        val info = StreakCalculator.compute(listOf(fullBlock(yesterdayNoon)), now, daysAgoMidnight(5))
        assertEquals(1, info.streakDays) // today is clean; yesterday broke it
        assertTrue(info.hasAnyBlocks)
        assertFalse(info.fullBlockToday)
    }

    @Test
    fun `full block today sets the flag and zeroes the streak`() {
        val info = StreakCalculator.compute(listOf(fullBlock(now)), now, daysAgoMidnight(5))
        assertEquals(0, info.streakDays)
        assertTrue(info.fullBlockToday)
    }

    @Test
    fun `non-full-block events never break the streak`() {
        val warning = ev(BlockReason.AI_DETECTION, "plain-match", now)
        val keyword = ev(BlockReason.KEYWORD_MATCH, "casino", now)
        val info = StreakCalculator.compute(listOf(warning, keyword), now, daysAgoMidnight(2))
        assertEquals(3, info.streakDays)
        assertFalse(info.fullBlockToday)
    }

    @Test
    fun `NOT_SENSITIVE audit events are ignored entirely`() {
        val audit = ev(BlockReason.NOT_SENSITIVE, null, now)
        val info = StreakCalculator.compute(listOf(audit), now, daysAgoMidnight(0))
        assertFalse(info.hasAnyBlocks)
        assertEquals(0, info.thisWeekBlocks)
    }

    // ------------------------------------------------------ weekly windows

    @Test
    fun `delta pct compares trailing 7 days with the previous 7`() {
        val weekMs = 7L * 24 * 60 * 60 * 1000L
        val thisWeek = (0 until 6).map {
            ev(BlockReason.KEYWORD_MATCH, "x", now - (it + 1) * 60_000L)
        }
        val lastWeek = (0 until 4).map {
            ev(BlockReason.KEYWORD_MATCH, "x", now - weekMs - (it + 1) * 60_000L)
        }
        val info = StreakCalculator.compute(thisWeek + lastWeek, now)
        assertEquals(6, info.thisWeekBlocks)
        assertEquals(4, info.lastWeekBlocks)
        assertEquals(50, info.deltaPct)
    }

    @Test
    fun `delta pct is null without a last-week baseline`() {
        val only = listOf(ev(BlockReason.KEYWORD_MATCH, "x", now - 60_000L))
        val info = StreakCalculator.compute(only, now)
        assertEquals(1, info.thisWeekBlocks)
        assertEquals(0, info.lastWeekBlocks)
        assertNull(info.deltaPct)
    }

    // --------------------------------------------------------- localDayStart

    @Test
    fun `localDayStart zeroes all sub-day fields and keeps the date`() {
        val start = StreakCalculator.localDayStart(now)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        assertEquals(nowCal.get(Calendar.DAY_OF_YEAR), cal.get(Calendar.DAY_OF_YEAR))
    }
}
