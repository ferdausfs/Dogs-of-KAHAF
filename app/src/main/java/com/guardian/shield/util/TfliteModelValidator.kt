package com.guardian.shield.util

import java.io.File

object TfliteModelValidator {
    private val TFLITE_MAGIC = byteArrayOf('T'.code.toByte(), 'F'.code.toByte(), 'L'.code.toByte(), '3'.code.toByte())

    fun hasValidHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 8L) return false
        return runCatching {
            file.inputStream().use { input ->
                val prefix = ByteArray(8)
                val read = input.read(prefix)
                read == 8 && prefix.copyOfRange(4, 8).contentEquals(TFLITE_MAGIC)
            }
        }.getOrDefault(false)
    }
}
