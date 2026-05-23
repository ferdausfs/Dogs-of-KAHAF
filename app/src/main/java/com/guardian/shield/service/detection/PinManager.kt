package com.guardian.shield.service.detection

import com.guardian.shield.data.local.datastore.SecureStorage
import com.guardian.shield.util.GuardianConstants
import com.guardian.shield.util.PinHasher
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
        val salt = PinHasher.newSalt()
        secure.putString(SecureStorage.KEY_PIN_SALT, salt)
        secure.putInt(SecureStorage.KEY_PIN_HASH_VERSION, 2)
        secure.putString(SecureStorage.KEY_PIN_HASH, PinHasher.hash(pin, salt))
        secure.putInt(SecureStorage.KEY_PIN_ATTEMPTS, 0)
        secure.putLong(SecureStorage.KEY_PIN_LOCKOUT_UNTIL, 0L)
        return true
    }

    fun verifyPin(pin: String): VerifyResult {
        val now = System.currentTimeMillis()
        val lockedUntil = secure.getLong(SecureStorage.KEY_PIN_LOCKOUT_UNTIL, 0L)
        if (now < lockedUntil) return VerifyResult.LockedOut(lockedUntil - now)

        val stored = secure.getString(SecureStorage.KEY_PIN_HASH) ?: return VerifyResult.NotSet
        val salt = secure.getString(SecureStorage.KEY_PIN_SALT)
        val isMatch = when {
            !salt.isNullOrBlank() -> stored == PinHasher.hash(pin, salt)
            else -> stored == PinHasher.legacyHash(pin)
        }

        return if (isMatch) {
            if (salt.isNullOrBlank()) {
                val newSalt = PinHasher.newSalt()
                secure.putString(SecureStorage.KEY_PIN_SALT, newSalt)
                secure.putInt(SecureStorage.KEY_PIN_HASH_VERSION, 2)
                secure.putString(SecureStorage.KEY_PIN_HASH, PinHasher.hash(pin, newSalt))
            }
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
        secure.remove(SecureStorage.KEY_PIN_SALT)
        secure.remove(SecureStorage.KEY_PIN_HASH_VERSION)
        secure.remove(SecureStorage.KEY_PIN_ATTEMPTS)
        secure.remove(SecureStorage.KEY_PIN_LOCKOUT_UNTIL)
    }

    sealed class VerifyResult {
        data object Success : VerifyResult()
        data class Wrong(val remainingAttempts: Int) : VerifyResult()
        data class LockedOut(val msRemaining: Long) : VerifyResult()
        data object NotSet : VerifyResult()
    }
}
