package com.guardian.shield.service.detection

import com.guardian.shield.data.local.datastore.SecureStorage
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v13 (2.1.3) STABILITY PATCH 3:
 *  • DEFENSIVE: every SecureStorage access is now runCatching-wrapped.
 *    The underlying EncryptedSharedPreferences can throw on
 *    Keystore-corrupted devices; we degrade gracefully (treat as
 *    "no PIN set") rather than crash the launching activity.
 */
@Singleton
class PinManager @Inject constructor(private val storage: SecureStorage) {
    companion object { private const val KEY_PIN_HASH = "pin_hash" }

    fun isPinSet(): Boolean = runCatching { storage.contains(KEY_PIN_HASH) }
        .onFailure { Timber.w(it, "isPinSet failed — treating as not set") }
        .getOrDefault(false)

    fun setPin(pin: String) {
        runCatching { storage.putString(KEY_PIN_HASH, hash(pin)) }
            .onFailure { Timber.w(it, "setPin failed (suppressed)") }
    }

    fun verifyPin(pin: String): Boolean = runCatching {
        storage.getString(KEY_PIN_HASH)?.let { it == hash(pin) } ?: false
    }.onFailure { Timber.w(it, "verifyPin failed — denying") }
        .getOrDefault(false)

    fun clearPin() {
        runCatching { storage.remove(KEY_PIN_HASH) }
            .onFailure { Timber.w(it, "clearPin failed (suppressed)") }
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
