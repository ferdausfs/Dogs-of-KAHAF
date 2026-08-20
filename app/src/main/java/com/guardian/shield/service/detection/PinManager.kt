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

    // =====================================================================
    // PHASE 1c (v3.5.0) — PIN recovery (DELIBERATELY non-trivial).
    //
    // Two paths, both with real friction. Nothing here weakens verifyPin():
    // a forgotten PIN can never be *bypassed* instantly — a valid recovery
    // code or a completed 48-hour wait is required, and both only lead to
    // "clear the old PIN so a new one can be set".
    //
    // Path A — one-time recovery code:
    //   Generated at PIN-setup time and shown exactly once. Only its salted
    //   PBKDF2 hash is stored (same derivation as the PIN itself); the
    //   plaintext never persists anywhere in the app, so it cannot be
    //   searched for. Verification is rate-limited (5 tries / 30 min).
    //
    // Path B — time-delayed reset:
    //   requestTimedReset() starts a 48h clock (wall-clock timestamp in
    //   encrypted storage). When it elapses the user may clear the PIN.
    //   A persistent warning notification is shown for the whole window and
    //   cancelling requires the PIN — mirror of the cooling-off philosophy:
    //   an impulsive reset takes 48 hours and stays visible the whole time.
    // =====================================================================

    /** True when a recovery code hash exists (code was generated at setup). */
    fun hasRecoveryCode(): Boolean =
        !secure.getString(SecureStorage.KEY_RECOVERY_HASH).isNullOrBlank()

    /**
     * Generate a fresh recovery code, persist ONLY its salted hash, and
     * return the displayable plaintext exactly once. Returns null when
     * secure storage is unavailable (fail closed, same policy as setPin).
     */
    fun generateRecoveryCode(): String? {
        if (!secure.isSecure) {
            Timber.e("Secure storage unavailable — refusing to generate recovery code")
            return null
        }
        val rng = SecureRandom()
        val raw = StringBuilder(RECOVERY_CODE_LENGTH)
        repeat(RECOVERY_CODE_LENGTH) { raw.append(RECOVERY_ALPHABET[rng.nextInt(RECOVERY_ALPHABET.length)]) }
        val code = raw.toString()
        val salt = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
        val hash = derive(code, salt, PIN_ITERATIONS, KEY_BITS)
        secure.putString(SecureStorage.KEY_RECOVERY_HASH, "${salt.toHex()}$SEPARATOR${hash.toHex()}")
        secure.putInt(SecureStorage.KEY_RECOVERY_ATTEMPTS, 0)
        secure.putLong(SecureStorage.KEY_RECOVERY_LOCKOUT_UNTIL, 0L)
        return formatRecoveryCode(code)
    }

    /**
     * Verify a typed recovery code (any casing, dashes/spaces optional).
     * Rate-limited: [GuardianConstants.PIN_RECOVERY_MAX_ATTEMPTS] failures →
     * lockout for [GuardianConstants.PIN_RECOVERY_LOCKOUT_MS].
     */
    fun verifyRecoveryCode(input: String): RecoveryResult {
        val now = System.currentTimeMillis()
        val lockedUntil = secure.getLong(SecureStorage.KEY_RECOVERY_LOCKOUT_UNTIL, 0L)
        if (now < lockedUntil) return RecoveryResult.LockedOut(lockedUntil - now)

        val stored = secure.getString(SecureStorage.KEY_RECOVERY_HASH)
            ?: return RecoveryResult.NotSet
        val normalized = input.filter { it.isLetterOrDigit() }.uppercase(java.util.Locale.US)
        if (normalized.length != RECOVERY_CODE_LENGTH) return failRecovery(now)

        return try {
            val parts = stored.split(SEPARATOR, limit = 2)
            val salt = parts[0].fromHex()
            val expected = parts[1].fromHex()
            if (MessageDigest.isEqual(derive(normalized, salt, PIN_ITERATIONS, KEY_BITS), expected)) {
                secure.putInt(SecureStorage.KEY_RECOVERY_ATTEMPTS, 0)
                RecoveryResult.Success
            } else {
                failRecovery(now)
            }
        } catch (t: Throwable) {
            Timber.e(t, "Recovery code verification failed")
            RecoveryResult.Wrong(-1)
        }
    }

    private fun failRecovery(now: Long): RecoveryResult {
        val attempts = secure.getInt(SecureStorage.KEY_RECOVERY_ATTEMPTS, 0) + 1
        secure.putInt(SecureStorage.KEY_RECOVERY_ATTEMPTS, attempts)
        return if (attempts >= GuardianConstants.PIN_RECOVERY_MAX_ATTEMPTS) {
            secure.putLong(
                SecureStorage.KEY_RECOVERY_LOCKOUT_UNTIL,
                now + GuardianConstants.PIN_RECOVERY_LOCKOUT_MS
            )
            secure.putInt(SecureStorage.KEY_RECOVERY_ATTEMPTS, 0)
            RecoveryResult.LockedOut(GuardianConstants.PIN_RECOVERY_LOCKOUT_MS)
        } else {
            RecoveryResult.Wrong(GuardianConstants.PIN_RECOVERY_MAX_ATTEMPTS - attempts)
        }
    }

    // --- Path B: time-delayed reset (wall-clock timestamp, encrypted store) ---

    /** Start the 48h reset clock. No-op if a request is already pending. */
    fun requestTimedReset() {
        if (timedResetRequestedAt() > 0L) return
        secure.putLong(SecureStorage.KEY_PIN_RESET_REQUESTED_AT, System.currentTimeMillis())
        Timber.w("PIN timed-reset REQUESTED — ${GuardianConstants.PIN_RESET_DELAY_MS}ms delay started")
    }

    /** Wall-clock time the reset was requested, or 0 when none is pending. */
    fun timedResetRequestedAt(): Long =
        secure.getLong(SecureStorage.KEY_PIN_RESET_REQUESTED_AT, 0L)

    /** True once the 48h wait has fully elapsed. */
    fun isTimedResetReady(): Boolean {
        val at = timedResetRequestedAt()
        return at > 0L &&
            System.currentTimeMillis() - at >= GuardianConstants.PIN_RESET_DELAY_MS
    }

    /** Milliseconds until the reset becomes actionable (0 when ready/none). */
    fun timedResetRemainingMs(): Long {
        val at = timedResetRequestedAt()
        if (at <= 0L) return 0L
        return (GuardianConstants.PIN_RESET_DELAY_MS - (System.currentTimeMillis() - at))
            .coerceAtLeast(0L)
    }

    /** Cancel a pending timed reset (caller must have verified the PIN). */
    fun cancelTimedReset() {
        secure.remove(SecureStorage.KEY_PIN_RESET_REQUESTED_AT)
        Timber.i("PIN timed-reset cancelled")
    }

    /**
     * Complete a reset: clear the PIN, any pending timed-reset request, and
     * the old recovery code hash (the new PIN setup will issue a fresh code).
     */
    fun completeReset() {
        clearPin()
        cancelTimedReset()
        secure.remove(SecureStorage.KEY_RECOVERY_HASH)
        secure.remove(SecureStorage.KEY_RECOVERY_ATTEMPTS)
        secure.remove(SecureStorage.KEY_RECOVERY_LOCKOUT_UNTIL)
        Timber.w("PIN reset COMPLETED — old PIN and recovery code cleared")
    }

    private fun formatRecoveryCode(raw: String): String =
        raw.chunked(4).joinToString("-")

    sealed class RecoveryResult {
        data object Success : RecoveryResult()
        data class Wrong(val remainingAttempts: Int) : RecoveryResult()
        data class LockedOut(val msRemaining: Long) : RecoveryResult()
        data object NotSet : RecoveryResult()
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
        // Unambiguous alphabet (no 0/O, 1/I/L) — 12 chars ≈ 2^59 entropy.
        const val RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        const val RECOVERY_CODE_LENGTH = 12
    }
}
