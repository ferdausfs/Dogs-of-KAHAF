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
 * v11 (2.1.1) STABILITY PATCH:
 *  • CRITICAL FIX: EncryptedSharedPreferences.create can throw on devices
 *    with broken Keystore implementations (some Mediatek / older Huawei
 *    builds and after factory-reset where the master key was lost).
 *    Previously this killed the app at startup. Now we:
 *      1. Try EncryptedSharedPreferences first (preferred path).
 *      2. On failure, attempt to delete the corrupted prefs file and
 *         re-create. (Common Keystore reset recovery.)
 *      3. As last resort, fall back to plain SharedPreferences. PIN is
 *         still SHA-256 hashed so the worst case is a non-encrypted
 *         hash on disk — strictly better than a crash.
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = createPreferences(context)

    private fun createPreferences(ctx: Context): SharedPreferences {
        return runCatching { buildEncrypted(ctx) }
            .recoverCatching {
                Timber.w(it, "EncryptedSharedPreferences failed — recovering corrupted prefs")
                runCatching {
                    ctx.getSharedPreferences("guardian_secure", Context.MODE_PRIVATE)
                        .edit().clear().commit()
                }
                buildEncrypted(ctx)
            }
            .getOrElse {
                Timber.e(it, "EncryptedSharedPreferences unavailable — falling back to plain prefs")
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

    fun putString(key: String, value: String) =
        runCatching { prefs.edit().putString(key, value).apply() }
            .onFailure { Timber.w(it, "putString failed") }
            .let { Unit }

    fun getString(key: String): String? =
        runCatching { prefs.getString(key, null) }.getOrNull()

    fun remove(key: String) =
        runCatching { prefs.edit().remove(key).apply() }
            .onFailure { Timber.w(it, "remove failed") }
            .let { Unit }

    fun contains(key: String): Boolean =
        runCatching { prefs.contains(key) }.getOrDefault(false)
}
