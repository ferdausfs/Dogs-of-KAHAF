package com.guardian.shield

import com.guardian.shield.util.ScreenTimeTracker
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R11 (v3.8.1) — unit tests for the compact screen-time formatter used by
 * the dashboard suggestion card and the Screen Time screen.
 */
class ScreenTimeFormatTest {

    @Test
    fun `zero and sub-hour values render as minutes only`() {
        assertEquals("0m", ScreenTimeTracker.formatMs(0L))
        assertEquals("37m", ScreenTimeTracker.formatMs(37L * 60 * 1000L))
        assertEquals("59m", ScreenTimeTracker.formatMs(59L * 60 * 1000L + 59_000L))
    }

    @Test
    fun `hour values render as h mm with zero padding`() {
        assertEquals("1h 00m", ScreenTimeTracker.formatMs(60L * 60 * 1000L))
        assertEquals("1h 35m", ScreenTimeTracker.formatMs(95L * 60 * 1000L))
        assertEquals("4h 12m", ScreenTimeTracker.formatMs(4L * 60 * 60 * 1000L + 12L * 60 * 1000L))
        assertEquals("24h 00m", ScreenTimeTracker.formatMs(24L * 60 * 60 * 1000L))
    }

    @Test
    fun `suggestion threshold constant is 45 minutes`() {
        assertEquals(45L * 60 * 1000L, ScreenTimeTracker.SUGGEST_AFTER_MS)
    }
}
