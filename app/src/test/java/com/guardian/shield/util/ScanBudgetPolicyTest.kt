package com.guardian.shield.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanBudgetPolicyTest {

    @Test
    fun `heavy scan blocked when battery low and not charging`() {
        val allowed = ScanBudgetPolicy.shouldRunHeavyScan(
            packageName = "com.instagram.android",
            isSafePackage = false,
            isScreenOn = true,
            protectionEnabled = true,
            isBlockingInProgress = false,
            isBatteryLow = true,
            isCharging = false,
            lastInteractionAt = 10_000,
            now = 12_000
        )

        assertFalse(allowed)
    }

    @Test
    fun `heavy scan allowed for active foreground app`() {
        val allowed = ScanBudgetPolicy.shouldRunHeavyScan(
            packageName = "com.instagram.android",
            isSafePackage = false,
            isScreenOn = true,
            protectionEnabled = true,
            isBlockingInProgress = false,
            isBatteryLow = false,
            isCharging = false,
            lastInteractionAt = 10_000,
            now = 12_000
        )

        assertTrue(allowed)
    }
}
