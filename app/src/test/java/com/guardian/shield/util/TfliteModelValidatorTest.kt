package com.guardian.shield.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TfliteModelValidatorTest {

    @Test
    fun `valid tflite header passes`() {
        val file = File.createTempFile("model", ".tflite")
        file.writeBytes(byteArrayOf(0x20, 0x00, 0x00, 0x00, 'T'.code.toByte(), 'F'.code.toByte(), 'L'.code.toByte(), '3'.code.toByte(), 0x00))

        assertTrue(TfliteModelValidator.hasValidHeader(file))
    }

    @Test
    fun `invalid tflite header fails`() {
        val file = File.createTempFile("model", ".tflite")
        file.writeBytes(byteArrayOf(0x20, 0x00, 0x00, 0x00, 'B'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte(), '!'.code.toByte()))

        assertFalse(TfliteModelValidator.hasValidHeader(file))
    }
}
