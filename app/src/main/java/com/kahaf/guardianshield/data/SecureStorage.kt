package com.kahaf.guardianshield.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted, defensive key/value store — ported from the legacy v2.x
 * codebase. Used for anything that should NOT live in the regular DataStore
 * (e.g. PIN hash extras, future device-admin tokens).
 *
 * v3.0.0 stability rules retained from the legacy build:
 *  • prefs is `by lazy` — defers Keystore I/O off the main thread.
 *  • EncryptedSharedPreferences.create can throw on devices with broken
 *    Keystore implementations (some Mediatek / older Huawei builds and
 *    after factory-reset where the master key was lost). We:
 *      1. Try EncryptedSharedPreferences first (preferred path).
 *      2. On failure, attempt to delete the corrupted prefs file and
 *         re-create.
 *      3. As last resort, fall back to plain SharedPreferences.
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences by lazy { createPreferences(context) }

    private fun createPreferences(ctx: Context): SharedPreferences {
        return runCatching { buildEncrypted(ctx) }
            .recoverCatching {
                Log.w(TAG, "EncryptedSharedPreferences failed — recovering corrupted prefs", it)
                runCatching {
                    ctx.getSharedPreferences("guardian_secure", Context.MODE_PRIVATE)
                        .edit().clear().commit()
                }
                buildEncrypted(ctx)
            }
            .getOrElse {
                Log.e(TAG, "EncryptedSharedPreferences unavailable — falling back to plain prefs", it)
                ctx.getSharedPreferences("guardian_secure_fallback", Context.MODE_PRIVATE)
            }
    }

    private fun buildEncrypted(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            ctx, "guardian_secure", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun putString(key: String, value: String) {
        runCatching { prefs.edit().putString(key, value).apply() }
            .onFailure { Log.w(TAG, "putString failed", it) }
    }

    fun getString(key: String): String? =
        runCatching { prefs.getString(key, null) }.getOrNull()

    fun remove(key: String) {
        runCatching { prefs.edit().remove(key).apply() }
            .onFailure { Log.w(TAG, "remove failed", it) }
    }

    fun contains(key: String): Boolean =
        runCatching { prefs.contains(key) }.getOrDefault(false)

    companion object { private const val TAG = "SecureStorage" }
}
