package com.kahaf.guardianshield.data

import java.security.MessageDigest

/**
 * Simple SHA-256 hashing for the 4-digit Settings PIN. The PIN is a child-lock
 * deterrent (not a hard cryptographic secret), so a plain SHA-256 of the digit
 * string is sufficient. The hash is stored in DataStore via [AppSettings].
 *
 * v3.0.0
 */
object PinManager {

    fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun verify(pin: String, hash: String): Boolean = hash(pin) == hash

    fun isValidFormat(pin: String): Boolean =
        pin.length == 4 && pin.all { it.isDigit() }
}
