package com.guardian.shield

import com.guardian.shield.service.dns.PrivateDnsScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * R11 (v3.8.1) — unit tests for the DNS Auto time math (R5 + R8 polish).
 * Everything here is pure: java.util.Calendar only, no Android runtime.
 */
class DnsScheduleMathTest {

    private fun at(
        year: Int, month: Int, day: Int, hour: Int, minute: Int,
        dayOfWeek: Int? = null
    ): Calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (dayOfWeek != null) set(Calendar.DAY_OF_WEEK, dayOfWeek)
    }

    // ---------------------------------------------------------- isInWindow

    @Test
    fun `same-day window contains its middle and start edge`() {
        assertTrue(PrivateDnsScheduler.isInWindow(12 * 60, 9 * 60, 18 * 60))
        assertTrue(PrivateDnsScheduler.isInWindow(9 * 60, 9 * 60, 18 * 60)) // start inclusive
    }

    @Test
    fun `same-day window excludes end edge and outside`() {
        assertFalse(PrivateDnsScheduler.isInWindow(18 * 60, 9 * 60, 18 * 60)) // end exclusive
        assertFalse(PrivateDnsScheduler.isInWindow(8 * 60 + 59, 9 * 60, 18 * 60))
        assertFalse(PrivateDnsScheduler.isInWindow(18 * 60 + 1, 9 * 60, 18 * 60))
    }

    @Test
    fun `overnight window wraps midnight on both sides`() {
        assertTrue(PrivateDnsScheduler.isInWindow(23 * 60, 20 * 60, 8 * 60))
        assertTrue(PrivateDnsScheduler.isInWindow(2 * 60, 20 * 60, 8 * 60))
        assertFalse(PrivateDnsScheduler.isInWindow(12 * 60, 20 * 60, 8 * 60))
    }

    @Test
    fun `zero-length window never matches`() {
        assertFalse(PrivateDnsScheduler.isInWindow(9 * 60, 9 * 60, 9 * 60))
    }

    // ------------------------------------------------------------ isPaused

    @Test
    fun `pause is active only while expiry is in the future`() {
        assertTrue(PrivateDnsScheduler.isPaused(2_000L, 1_000L))
        assertFalse(PrivateDnsScheduler.isPaused(1_000L, 1_000L))
        assertFalse(PrivateDnsScheduler.isPaused(500L, 1_000L))
        assertFalse(PrivateDnsScheduler.isPaused(0L, 1_000L))
    }

    // ------------------------------------------------------------ dayIndex

    @Test
    fun `dayIndex maps Sunday to 0 through Saturday to 6`() {
        assertEquals(0, PrivateDnsScheduler.dayIndex(at(2026, 7, 25, 12, 0, Calendar.SUNDAY)))
        assertEquals(1, PrivateDnsScheduler.dayIndex(at(2026, 7, 25, 12, 0, Calendar.MONDAY)))
        assertEquals(3, PrivateDnsScheduler.dayIndex(at(2026, 7, 25, 12, 0, Calendar.WEDNESDAY)))
        assertEquals(6, PrivateDnsScheduler.dayIndex(at(2026, 7, 25, 12, 0, Calendar.SATURDAY)))
    }

    // -------------------------------------------------------- isEffectiveNow

    private fun eff(
        nowMin: Int, startMin: Int, endMin: Int,
        dayMask: Int = 0b1111111, pauseUntil: Long = 0L, now: Calendar
    ) = PrivateDnsScheduler.isEffectiveNow(nowMin, startMin, endMin, dayMask, pauseUntil, now)

    @Test
    fun `effective inside window on a selected day`() {
        val mondayAt22 = at(2026, 7, 24, 22, 0, Calendar.MONDAY)
        assertTrue(eff(22 * 60, 20 * 60, 8 * 60, dayMask = 1 shl 1, now = mondayAt22))
    }

    @Test
    fun `not effective inside window when today is unselected`() {
        val tuesdayAt22 = at(2026, 7, 25, 22, 0, Calendar.TUESDAY)
        assertFalse(eff(22 * 60, 20 * 60, 8 * 60, dayMask = 1 shl 1, now = tuesdayAt22))
    }

    @Test
    fun `overnight spillover belongs to yesterday`() {
        // Saturday 02:00 inside a 20:00->08:00 window: Friday IS selected.
        val satMorning = at(2026, 7, 29, 2, 0, Calendar.SATURDAY)
        assertTrue(eff(2 * 60, 20 * 60, 8 * 60, dayMask = 1 shl 5, now = satMorning))
        // Tuesday 02:00: spillover belongs to Monday; Monday NOT selected.
        val tueMorning = at(2026, 7, 25, 2, 0, Calendar.TUESDAY)
        assertFalse(eff(2 * 60, 20 * 60, 8 * 60, dayMask = 1 shl 1, now = tueMorning))
    }

    @Test
    fun `pause suppresses an otherwise effective moment`() {
        val mondayAt22 = at(2026, 7, 24, 22, 0, Calendar.MONDAY)
        val pauseUntil = mondayAt22.timeInMillis + 10 * 60 * 1000L
        assertFalse(
            eff(22 * 60, 20 * 60, 8 * 60, dayMask = 0b1111111, pauseUntil = pauseUntil, now = mondayAt22)
        )
    }

    // --------------------------------------------------- nextBoundaryMillis

    @Test
    fun `no boundary when disabled or host blank`() {
        val now = at(2026, 7, 24, 10, 0)
        assertEquals(
            0L,
            PrivateDnsScheduler.nextBoundaryMillis(
                PrivateDnsScheduler.Cache(false, 20 * 60, 8 * 60, "dns.example"), now
            )
        )
        assertEquals(
            0L,
            PrivateDnsScheduler.nextBoundaryMillis(
                PrivateDnsScheduler.Cache(true, 20 * 60, 8 * 60, ""), now
            )
        )
    }

    @Test
    fun `next boundary is the sooner of today or tomorrow start end`() {
        val at10 = at(2026, 7, 24, 10, 0) // 10:00
        val c = PrivateDnsScheduler.Cache(true, 20 * 60, 8 * 60, "dns.example")
        val expected = at(2026, 7, 24, 20, 0).timeInMillis // today 20:00
        assertEquals(expected, PrivateDnsScheduler.nextBoundaryMillis(c, at10))

        val at23 = at(2026, 7, 24, 23, 0) // 23:00 -> tomorrow 08:00
        val expected2 = at(2026, 7, 25, 8, 0).timeInMillis
        assertEquals(expected2, PrivateDnsScheduler.nextBoundaryMillis(c, at23))
    }

    @Test
    fun `active pause adds an earlier boundary at its expiry`() {
        val at19 = at(2026, 7, 24, 19, 0) // 19:00, window starts 20:00
        val pauseUntil = at19.timeInMillis + 15 * 60 * 1000L // 19:15 — before 20:00
        val c = PrivateDnsScheduler.Cache(true, 20 * 60, 8 * 60, "dns.example", 0b1111111, pauseUntil)
        assertEquals(pauseUntil, PrivateDnsScheduler.nextBoundaryMillis(c, at19))
    }

    @Test
    fun `expired pause is ignored as a boundary`() {
        val at19 = at(2026, 7, 24, 19, 0)
        val pauseUntil = at19.timeInMillis - 1_000L // already past
        val c = PrivateDnsScheduler.Cache(true, 20 * 60, 8 * 60, "dns.example", 0b1111111, pauseUntil)
        assertEquals(at(2026, 7, 24, 20, 0).timeInMillis, PrivateDnsScheduler.nextBoundaryMillis(c, at19))
    }
}
