package com.guardian.shield.service.detection

import com.guardian.shield.data.local.datastore.SecureStorage
import com.guardian.shield.util.GuardianConstants
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinManager @Inject constructor(
    private val secure: SecureStorage
) {
    fun isPinSet(): Boolean =
        !secure.getString(SecureStorage.KEY_PIN_HASH).isNullOrBlank()

    fun setPin(pin: String): Boolean {
        if (pin.length !in 4..6 || !pin.all { it.isDigit() }) return false
        // Fail closed: never persist the PIN unless the backing store is
        // genuinely encrypted.
        if (!secure.isSecure) {
            Timber.e("Secure storage unavailable — refusing to store PIN")
            return false
        }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt, PIN_ITERATIONS, KEY_BITS)
        secure.putString(SecureStorage.KEY_PIN_HASH, "${salt.toHex()}$SEPARATOR${hash.toHex()}")
        secure.putInt(SecureStorage.KEY_PIN_ATTEMPTS, 0)
        secure.putLong(SecureStorage.KEY_PIN_LOCKOUT_UNTIL, 0L)
        return true
    }

    fun verifyPin(pin: String): VerifyResult {
        val now = System.currentTimeMillis()
        val lockedUntil = secure.getLong(SecureStorage.KEY_PIN_LOCKOUT_UNTIL, 0L)
        if (now < lockedUntil) return VerifyResult.LockedOut(lockedUntil - now)

        val stored = secure.getString(SecureStorage.KEY_PIN_HASH) ?: return VerifyResult.NotSet
        return if (verifyStored(stored, pin)) {
            secure.putInt(SecureStorage.KEY_PIN_ATTEMPTS, 0)
            VerifyResult.Success
        } else {
            val attempts = secure.getInt(SecureStorage.KEY_PIN_ATTEMPTS, 0) + 1
            secure.putInt(SecureStorage.KEY_PIN_ATTEMPTS, attempts)
            if (attempts >= GuardianConstants.PIN_MAX_ATTEMPTS) {
                secure.putLong(SecureStorage.KEY_PIN_LOCKOUT_UNTIL, now + GuardianConstants.PIN_LOCKOUT_MS)
                secure.putInt(SecureStorage.KEY_PIN_ATTEMPTS, 0)
                VerifyResult.LockedOut(GuardianConstants.PIN_LOCKOUT_MS)
            } else {
                VerifyResult.Wrong(GuardianConstants.PIN_MAX_ATTEMPTS - attempts)
            }
        }
    }

    fun clearPin() {
        secure.remove(SecureStorage.KEY_PIN_HASH)
        secure.remove(SecureStorage.KEY_PIN_ATTEMPTS)
        secure.remove(SecureStorage.KEY_PIN_LOCKOUT_UNTIL)
    }

    /**
     * Verify against either the current salted format ("<saltHex>:<hashHex>")
     * or the legacy unsalted SHA-256 format. Legacy matches are transparently
     * migrated to the salted format.
     */
    private fun verifyStored(stored: String, pin: String): Boolean = try {
        val parts = stored.split(SEPARATOR, limit = 2)
        if (parts.size == 2) {
            val salt = parts[0].fromHex()
            val expected = parts[1].fromHex()
            MessageDigest.isEqual(derive(pin, salt, PIN_ITERATIONS, KEY_BITS), expected)
        } else {
            // Legacy unsalted SHA-256: reproduce the exact legacy encoding and
            // compare as strings (identical to the pre-existing behaviour).
            val legacy = MessageDigest.getInstance("SHA-256")
                .digest(pin.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val matches = stored == legacy
            if (matches) migrateToSalted(pin)
            matches
        }
    } catch (t: Throwable) {
        Timber.e(t, "PIN verification failed")
        false
    }

    private fun migrateToSalted(pin: String) {
        try { setPin(pin) } catch (t: Throwable) { Timber.e(t, "PIN migration failed") }
    }

    /** Salted, key-stretched derivation via PBKDF2-HMAC-SHA256. */
    private fun derive(pin: String, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, keyBits)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Encode as two hex chars per byte (mask to unsigned to avoid sign-extension). */
    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun String.fromHex(): ByteArray {
        require(length % 2 == 0) { "Invalid hex string" }
        return ByteArray(length / 2) {
            ((this[it * 2].digitToInt(16) shl 4) or this[it * 2 + 1].digitToInt(16)).toByte()
        }
    }

    sealed class VerifyResult {
        data object Success : VerifyResult()
        data class Wrong(val remainingAttempts: Int) : VerifyResult()
        data class LockedOut(val msRemaining: Long) : VerifyResult()
        data object NotSet : VerifyResult()
    }

    private companion object {
        const val PIN_ITERATIONS = 120_000
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
        const val SEPARATOR = ":"
    }
}
