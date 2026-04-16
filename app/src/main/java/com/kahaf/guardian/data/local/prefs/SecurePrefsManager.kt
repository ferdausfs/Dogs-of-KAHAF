package com.kahaf.guardian.data.local.prefs

import android.content.SharedPreferences
import com.kahaf.guardian.util.Constants
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePrefsManager @Inject constructor(private val prefs: SharedPreferences) {
    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }

    fun isPinSet(): Boolean = prefs.getString("pin_hash", null) != null
    fun setPin(pin: String) = prefs.edit().putString("pin_hash", hash(pin)).apply()
    fun verifyPin(pin: String): Boolean = hash(pin) == prefs.getString("pin_hash", null)
    fun isProtectionActive(): Boolean = prefs.getBoolean("protection_active", true)
    fun setProtectionActive(v: Boolean) = prefs.edit().putBoolean("protection_active", v).apply()
    fun isKeywordDetectionEnabled(): Boolean = prefs.getBoolean("keyword_detection", true)
    fun setKeywordDetectionEnabled(v: Boolean) = prefs.edit().putBoolean("keyword_detection", v).apply()
    fun isAiDetectionEnabled(): Boolean = prefs.getBoolean("ai_detection", false)
    fun setAiDetectionEnabled(v: Boolean) = prefs.edit().putBoolean("ai_detection", v).apply()
    fun isStrictModeEnabled(): Boolean = prefs.getBoolean("strict_mode", false)
    fun setStrictModeEnabled(v: Boolean) = prefs.edit().putBoolean("strict_mode", v).apply()
    fun getDelaySeconds(): Int = prefs.getInt("delay_seconds", Constants.DEFAULT_DELAY_SECONDS)
    fun setDelaySeconds(v: Int) = prefs.edit().putInt("delay_seconds", v.coerceIn(10, 300)).apply()
}
