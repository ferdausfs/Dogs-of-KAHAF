package com.guardian.shield

import com.guardian.shield.service.detection.LockShield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R14 (v3.8.3) — guards the Commitment Tamper-Shield package set.
 */
class LockShieldTest {

    @Test
    fun `core tamper surfaces are shielded`() {
        assertTrue(LockShield.isTarget("com.android.settings"))
        assertTrue(LockShield.isTarget("com.android.vending"))
        assertTrue(LockShield.isTarget("com.google.android.packageinstaller"))
        assertTrue(LockShield.isTarget("com.android.packageinstaller"))
        assertTrue(LockShield.isTarget("com.samsung.android.packageinstaller"))
        assertTrue(LockShield.isTarget("com.miui.securitycenter"))
    }

    @Test
    fun `ordinary apps and our own package are never shielded`() {
        assertFalse(LockShield.isTarget("com.guardian.shield"))
        assertFalse(LockShield.isTarget("com.guardian.shield.debug"))
        assertFalse(LockShield.isTarget("com.facebook.katana"))
        assertFalse(LockShield.isTarget("com.chrome.browser"))
        assertFalse(LockShield.isTarget(""))
    }

    @Test
    fun `overlay detail token matches activity copy contract`() {
        assertEquals("lockshield_active", LockShield.DETAIL)
    }

    @Test
    fun `set stays exactly six packages`() {
        assertEquals(6, LockShield.PACKAGES.size)
    }
}
