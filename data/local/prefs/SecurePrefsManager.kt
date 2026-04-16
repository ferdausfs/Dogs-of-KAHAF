package com.kahaf.guardian.data.local.prefs

import android.content.SharedPreferences
import com.kahaf.guardian.util.Constants
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePrefsManager @Inject constructor(
    private val encryptedPrefs: SharedPreferences
) {
    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PROTECTION_ACTIVE = "protection_active"
        private const val KEY_KEYWORD_DETECTION = "keyword_detection"
        private const val KEY_AI_DETECTION = "ai_detection"
        private const val KEY_STRICT_MODE = "strict_mode"
        private const val KEY_DELAY_SECONDS = "delay_seconds"
    }

    // --- PIN Management ---

    fun isPinSet(): Boolean {
        return encryptedPrefs.getString(KEY_PIN_HASH, null) != null
    }

    fun setPin(pin: String) {
        val hash = hashPin(pin)
        encryptedPrefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = encryptedPrefs.getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(pin) == storedHash
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    // --- Protection State ---

    fun isProtectionActive(): Boolean {
        return encryptedPrefs.getBoolean(KEY_PROTECTION_ACTIVE, true)
    }

    fun setProtectionActive(active: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_PROTECTION_ACTIVE, active).apply()
    }

    // --- Detection Settings ---

    fun isKeywordDetectionEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_KEYWORD_DETECTION, true)
    }

    fun setKeywordDetectionEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_KEYWORD_DETECTION, enabled).apply()
    }

    fun isAiDetectionEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_AI_DETECTION, false)
    }

    fun setAiDetectionEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_AI_DETECTION, enabled).apply()
    }

    fun isStrictModeEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_STRICT_MODE, false)
    }

    fun setStrictModeEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_STRICT_MODE, enabled).apply()
    }

    // --- Delay ---

    fun getDelaySeconds(): Int {
        return encryptedPrefs.getInt(KEY_DELAY_SECONDS, Constants.DEFAULT_DELAY_SECONDS)
    }

    fun setDelaySeconds(seconds: Int) {
        encryptedPrefs.edit().putInt(KEY_DELAY_SECONDS, seconds.coerceIn(10, 300)).apply()
    }
}