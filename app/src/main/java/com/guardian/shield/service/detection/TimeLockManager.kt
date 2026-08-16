package com.guardian.shield.service.detection

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.guardian.shield.util.InMemoryPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * তিনটা State:
 *  UNLOCKED  → lock নেই, সব edit করা যাবে
 *  LOCKED    → চিরকাল lock, কোনো edit নেই, unlock request দেওয়া যাবে
 *  COOLDOWN  → unlock request দেওয়া হয়েছে, countdown চলছে — এখনো edit বন্ধ
 *             Cooldown শেষ হলে → UNLOCKED
 */
@Singleton
class TimeLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context, "guardian_timelock", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (t: Throwable) {
            Timber.e(t, "EncryptedSharedPreferences failed; lock state will not persist (no plaintext fallback)")
            InMemoryPreferences()
        }
    }

    companion object {
        private const val KEY_LOCK_ACTIVE    = "lock_active"
        private const val KEY_COOLDOWN_MS    = "cooldown_duration_ms"
        private const val KEY_COOLDOWN_END   = "cooldown_end_time"
        private const val KEY_LOCK_LABEL     = "lock_label"

        val DURATIONS = listOf(
            Pair("১ দিন",   24 * 60 * 60 * 1_000L),
            Pair("৩ দিন",   3 * 24 * 60 * 60 * 1_000L),
            Pair("৭ দিন",   7 * 24 * 60 * 60 * 1_000L),
            Pair("১৫ দিন", 15 * 24 * 60 * 60 * 1_000L),
            Pair("৩০ দিন", 30 * 24 * 60 * 60 * 1_000L)
        )
    }

    // ── State queries ────────────────────────────────────────────

    fun isLocked(): Boolean {
        if (!prefs.getBoolean(KEY_LOCK_ACTIVE, false)) return false
        // Cooldown শেষ হয়ে গেলে unlock
        val cooldownEnd = prefs.getLong(KEY_COOLDOWN_END, 0L)
        if (cooldownEnd > 0L && System.currentTimeMillis() >= cooldownEnd) {
            clearLock()
            return false
        }
        return true
    }

    fun isInCooldown(): Boolean {
        if (!prefs.getBoolean(KEY_LOCK_ACTIVE, false)) return false
        val cooldownEnd = prefs.getLong(KEY_COOLDOWN_END, 0L)
        return cooldownEnd > 0L && System.currentTimeMillis() < cooldownEnd
    }

    fun isUnlockRequested(): Boolean = prefs.getLong(KEY_COOLDOWN_END, 0L) > 0L

    // ── Actions ──────────────────────────────────────────────────

    /** Lock set করো — indefinitely locked, duration = cooldown period */
    fun setLock(cooldownDurationMs: Long, label: String) {
        prefs.edit()
            .putBoolean(KEY_LOCK_ACTIVE, true)
            .putLong(KEY_COOLDOWN_MS, cooldownDurationMs)
            .putString(KEY_LOCK_LABEL, label)
            .putLong(KEY_COOLDOWN_END, 0L) // cooldown এখনো শুরু হয়নি
            .apply()
        Timber.i("TimeLock SET: $label, cooldown=${cooldownDurationMs}ms")
    }

    /** Unlock request → cooldown শুরু হয় */
    fun requestUnlock() {
        val cooldownMs = prefs.getLong(KEY_COOLDOWN_MS, DURATIONS[0].second)
        val endTime = System.currentTimeMillis() + cooldownMs
        prefs.edit().putLong(KEY_COOLDOWN_END, endTime).apply()
        Timber.i("Unlock requested, cooldown ends: ${formatTime(endTime)}")
    }

    /** Cooldown চলছে কিনা check করে clear করে */
    fun clearIfExpired() {
        val cooldownEnd = prefs.getLong(KEY_COOLDOWN_END, 0L)
        if (cooldownEnd > 0L && System.currentTimeMillis() >= cooldownEnd) {
            clearLock()
        }
    }

    /** Force clear (admin only) */
    fun clearLock() {
        prefs.edit()
            .remove(KEY_LOCK_ACTIVE)
            .remove(KEY_COOLDOWN_MS)
            .remove(KEY_COOLDOWN_END)
            .remove(KEY_LOCK_LABEL)
            .apply()
    }

    // ── Info ─────────────────────────────────────────────────────

    fun getLockLabel(): String = prefs.getString(KEY_LOCK_LABEL, "") ?: ""

    fun getCooldownDurationMs(): Long = prefs.getLong(KEY_COOLDOWN_MS, DURATIONS[0].second)

    fun getCooldownEndFormatted(): String = formatTime(prefs.getLong(KEY_COOLDOWN_END, 0L))

    fun getCooldownRemainingMs(): Long =
        (prefs.getLong(KEY_COOLDOWN_END, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    /** isLocked() এর জন্য: edit blocked থাকলে কতক্ষণ */
    fun getRemainingFormatted(): String {
        return if (isInCooldown()) {
            val ms = getCooldownRemainingMs()
            formatMs(ms) + " পর unlock হবে"
        } else if (isLocked()) {
            "Unlock request দিন"
        } else "শেষ"
    }

    fun getCooldownLabel(): String {
        val ms = prefs.getLong(KEY_COOLDOWN_MS, 0L)
        return DURATIONS.firstOrNull { it.second == ms }?.first ?: formatMs(ms)
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun formatMs(ms: Long): String {
        if (ms <= 0) return "শেষ"
        val days  = ms / (24 * 60 * 60 * 1_000L)
        val hours = (ms % (24 * 60 * 60 * 1_000L)) / (60 * 60 * 1_000L)
        val mins  = (ms % (60 * 60 * 1_000L)) / (60 * 1_000L)
        return when {
            days > 0  -> "${days} দিন ${hours} ঘণ্টা"
            hours > 0 -> "${hours} ঘণ্টা ${mins} মিনিট"
            else      -> "${mins} মিনিট"
        }
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(ms))

    // Legacy compat — পুরনো code এ getEndTimeFormatted() ছিল
    fun getEndTimeFormatted(): String = getCooldownEndFormatted()
}
