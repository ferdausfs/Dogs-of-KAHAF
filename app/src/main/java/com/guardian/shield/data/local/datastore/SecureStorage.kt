package com.guardian.shield.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for sensitive data (PIN hash + salt).
 * Uses EncryptedSharedPreferences backed by Android Keystore.
 *
 * FIX: Fallback to unencrypted storage removed — PIN hash must never
 * be stored unencrypted. If EncryptedSharedPreferences fails, all
 * operations return safe defaults (null / false).
 */
@Singleton
class SecureStorage @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME   = "guardian_secure"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SET  = "pin_set"
        private const val KEY_SALT     = "pin_salt"
        private const val TAG          = "SecureStorage"
    }

    // FIX: Nullable — if init fails, operations return safe defaults
    private val prefs: SharedPreferences? by lazy { buildPrefs() }

    private fun buildPrefs(): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // FIX: No unencrypted fallback — PIN hash must stay encrypted
            Timber.e(e, "$TAG EncryptedSharedPreferences init failed — no fallback")
            null
        }
    }

    fun isPinSet(): Boolean = try {
        prefs?.getBoolean(KEY_PIN_SET, false) ?: false
    } catch (e: Exception) {
        Timber.e(e, "$TAG isPinSet failed")
        false
    }

    fun getPinHash(): String? = try {
        prefs?.getString(KEY_PIN_HASH, null)
    } catch (e: Exception) {
        Timber.e(e, "$TAG getPinHash failed")
        null
    }

    fun savePinHash(hash: String) {
        try {
            prefs?.edit()
                ?.putString(KEY_PIN_HASH, hash)
                ?.putBoolean(KEY_PIN_SET, true)
                ?.apply()
        } catch (e: Exception) {
            Timber.e(e, "$TAG savePinHash failed")
        }
    }

    // FIX: getSalt / saveSalt for PBKDF2 support
    fun getSalt(): String? = try {
        prefs?.getString(KEY_SALT, null)
    } catch (e: Exception) {
        Timber.e(e, "$TAG getSalt failed")
        null
    }

    fun saveSalt(salt: String) {
        try {
            prefs?.edit()
                ?.putString(KEY_SALT, salt)
                ?.apply()
        } catch (e: Exception) {
            Timber.e(e, "$TAG saveSalt failed")
        }
    }

    // FIX: remove() instead of putBoolean(false) — consistent state
    fun clearPin() {
        try {
            prefs?.edit()
                ?.remove(KEY_PIN_HASH)
                ?.remove(KEY_PIN_SET)
                ?.remove(KEY_SALT)
                ?.apply()
        } catch (e: Exception) {
            Timber.e(e, "$TAG clearPin failed")
        }
    }
}