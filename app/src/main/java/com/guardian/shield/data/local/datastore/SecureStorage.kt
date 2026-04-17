package com.guardian.shield.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for sensitive data (PIN hash).
 * Uses EncryptedSharedPreferences backed by Android Keystore.
 * PIN is stored as SHA-256 hash — never plain text.
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "guardian_secure"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SET = "pin_set"
    }

    private val prefs: SharedPreferences by lazy { buildPrefs() }

    private fun buildPrefs(): SharedPreferences {
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
            Timber.e(e, "EncryptedSharedPreferences init failed — falling back")
            // Fallback for device-level issues (rare)
            context.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    fun savePinHash(hash: String) {
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_PIN_SET, true)
            .apply()
    }

    fun getPinHash(): String? = prefs.getString(KEY_PIN_HASH, null)

    fun isPinSet(): Boolean = prefs.getBoolean(KEY_PIN_SET, false)

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_PIN_SET, false)
            .apply()
    }
}
