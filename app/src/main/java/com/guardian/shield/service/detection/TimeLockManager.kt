package com.guardian.shield.service.detection

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

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
            Timber.e(t, "EncryptedSharedPreferences failed, using fallback")
            context.getSharedPreferences("guardian_timelock_fb", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_LOCK_END = "lock_end_time"
        private const val KEY_LOCK_LABEL = "lock_label"

        val DURATIONS = listOf(
            Pair("১ দিন", 24 * 60 * 60 * 1_000L),
            Pair("৩ দিন", 3 * 24 * 60 * 60 * 1_000L),
            Pair("৭ দিন", 7 * 24 * 60 * 60 * 1_000L),
            Pair("১৫ দিন", 15 * 24 * 60 * 60 * 1_000L),
            Pair("৩০ দিন", 30 * 24 * 60 * 60 * 1_000L)
        )
    }

    fun setLock(durationMs: Long, label: String) {
        val endTime = System.currentTimeMillis() + durationMs
        prefs.edit().putLong(KEY_LOCK_END, endTime).putString(KEY_LOCK_LABEL, label).apply()
        Timber.i("TimeLock: $label until ${formatTime(endTime)}")
    }

    fun isLocked(): Boolean = System.currentTimeMillis() < prefs.getLong(KEY_LOCK_END, 0L)

    fun getRemainingMs(): Long = (prefs.getLong(KEY_LOCK_END, 0L) - System.currentTimeMillis()).coerceAtLeast(0)

    fun getEndTimeFormatted(): String = formatTime(prefs.getLong(KEY_LOCK_END, 0L))

    fun getLockLabel(): String = prefs.getString(KEY_LOCK_LABEL, "") ?: ""

    fun getRemainingFormatted(): String {
        val ms = getRemainingMs()
        if (ms <= 0) return "শেষ"
        val days = ms / (24 * 60 * 60 * 1_000L)
        val hours = (ms % (24 * 60 * 60 * 1_000L)) / (60 * 60 * 1_000L)
        val mins = (ms % (60 * 60 * 1_000L)) / (60 * 1_000L)
        return when {
            days > 0 -> "${days} দিন ${hours} ঘণ্টা বাকি"
            hours > 0 -> "${hours} ঘণ্টা ${mins} মিনিট বাকি"
            else -> "${mins} মিনিট বাকি"
        }
    }

    fun clearIfExpired() {
        if (!isLocked()) prefs.edit().remove(KEY_LOCK_END).remove(KEY_LOCK_LABEL).apply()
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(ms))
}