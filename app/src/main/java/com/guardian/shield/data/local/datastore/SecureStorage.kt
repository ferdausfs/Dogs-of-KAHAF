package com.guardian.shield.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.guardian.shield.util.InMemoryPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * True only when the backing store is genuinely encrypted. When false the
     * store is a non-persistent in-memory map and callers MUST NOT persist
     * secrets (see [PinManager.setPin]).
     */
    @Volatile var isSecure: Boolean = true
        private set

    private val prefs: SharedPreferences by lazy { createPrefs() }

    private fun createPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (t: Throwable) {
            isSecure = false
            Timber.e(t, "EncryptedSharedPreferences init failed — secure storage disabled (no plaintext fallback)")
            InMemoryPreferences()
        }
    }

    fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, default: String? = null): String? =
        prefs.getString(key, default)

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, default: Long = 0L): Long = prefs.getLong(key, default)

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, default: Int = 0): Int = prefs.getInt(key, default)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    companion object {
        private const val FILE_NAME = "guardian_secure"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_ATTEMPTS = "pin_attempts"
        const val KEY_PIN_LOCKOUT_UNTIL = "pin_lockout_until"
    }
}
