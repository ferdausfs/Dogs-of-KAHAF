package com.guardian.shield.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `newSalt generates non blank values`() {
        val first = PinHasher.newSalt()
        val second = PinHasher.newSalt()

        assertTrue(first.isNotBlank())
        assertTrue(second.isNotBlank())
        assertNotEquals(first, second)
    }

    @Test
    fun `hash is stable for same pin and salt`() {
        val salt = "fixedSalt"
        val one = PinHasher.hash("1234", salt)
        val two = PinHasher.hash("1234", salt)

        assertEquals(one, two)
        assertNotEquals(one, PinHasher.hash("9999", salt))
    }
}
