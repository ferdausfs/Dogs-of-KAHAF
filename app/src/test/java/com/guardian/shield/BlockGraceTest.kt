package com.guardian.shield

import com.guardian.shield.domain.model.AppRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R12 (v3.8.2) — unit tests for the 3-minute accidental-block undo grace.
 */
class BlockGraceTest {

    private val now: Long = 1_700_000_000_000L

    @Test
    fun `grace window is exactly three minutes`() {
        assertEquals(3L * 60 * 1000L, AppRule.BLOCK_GRACE_MS)
    }

    @Test
    fun `fresh block is within grace`() {
        assertTrue(AppRule.isWithinGrace(now - 5_000L, now))
        assertTrue(AppRule.isWithinGrace(now, now))
    }

    @Test
    fun `boundary at exactly three minutes still counts`() {
        assertTrue(AppRule.isWithinGrace(now - AppRule.BLOCK_GRACE_MS, now))
    }

    @Test
    fun `one millisecond past the window is permanent`() {
        assertFalse(AppRule.isWithinGrace(now - AppRule.BLOCK_GRACE_MS - 1L, now))
    }

    @Test
    fun `legacy blocks without a stamp never get grace`() {
        assertFalse(AppRule.isWithinGrace(0L, now))
    }

    @Test
    fun `clock skew backwards does not cancel grace`() {
        // Stamp 10s in the future — treat as within grace on purpose.
        assertTrue(AppRule.isWithinGrace(now + 10_000L, now))
    }

    @Test
    fun `remaining time floors at zero and caps at the window`() {
        assertEquals(0L, AppRule.graceRemainingMs(0L, now))
        assertEquals(0L, AppRule.graceRemainingMs(now - AppRule.BLOCK_GRACE_MS - 1_000L, now))
        assertEquals(AppRule.BLOCK_GRACE_MS, AppRule.graceRemainingMs(now, now))
        assertEquals(60_000L, AppRule.graceRemainingMs(now - AppRule.BLOCK_GRACE_MS + 60_000L, now))
    }
}
