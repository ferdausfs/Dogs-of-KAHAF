package com.guardian.shield.service.detection

import com.guardian.shield.data.local.datastore.SecureStorage
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinManager @Inject constructor(private val storage: SecureStorage) {
    companion object { private const val KEY_PIN_HASH = "pin_hash" }

    fun isPinSet(): Boolean = storage.contains(KEY_PIN_HASH)

    fun setPin(pin: String) { storage.putString(KEY_PIN_HASH, hash(pin)) }

    fun verifyPin(pin: String): Boolean =
        storage.getString(KEY_PIN_HASH)?.let { it == hash(pin) } ?: false

    fun clearPin() = storage.remove(KEY_PIN_HASH)

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
