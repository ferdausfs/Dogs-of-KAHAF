package com.guardianshield.app.manager

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.guardianshield.app.util.Constants
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Stores parent PIN as salted SHA-256 hash inside EncryptedSharedPreferences.
 */
object PinManager {

    private fun prefs(ctx: Context) = run {
        val masterKey = MasterKey.Builder(ctx.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx.applicationContext,
            Constants.PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasPin(ctx: Context): Boolean =
        prefs(ctx).getString(Constants.PREF_PIN_HASH, null) != null

    fun setPin(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs(ctx).edit()
            .putString(Constants.PREF_PIN_SALT, toHex(salt))
            .putString(Constants.PREF_PIN_HASH, hash)
            .apply()
    }

    fun verify(ctx: Context, pin: String): Boolean {
        val p = prefs(ctx)
        val saltHex = p.getString(Constants.PREF_PIN_SALT, null) ?: return false
        val storedHash = p.getString(Constants.PREF_PIN_HASH, null) ?: return false
        return hash(pin, fromHex(saltHex)) == storedHash
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(pin.toByteArray(Charsets.UTF_8))
        return toHex(md.digest())
    }

    private fun toHex(b: ByteArray): String =
        b.joinToString("") { "%02x".format(it) }

    private fun fromHex(s: String): ByteArray =
        ByteArray(s.length / 2) { ((Character.digit(s[it * 2], 16) shl 4) + Character.digit(s[it * 2 + 1], 16)).toByte() }
}
