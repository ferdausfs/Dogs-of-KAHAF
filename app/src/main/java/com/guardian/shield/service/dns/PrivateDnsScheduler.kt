package com.guardian.shield.service.dns

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import timber.log.Timber
import java.util.Calendar

/**
 * Time math + exact boundary alarms for DNS Auto Mode (R5).
 *
 * The schedule is mirrored into a tiny plain SharedPreferences cache so the
 * alarm receiver and workers can read it SYNCHRONOUSLY (DataStore is async;
 * receivers can't suspend). DataStore stays the source of truth for the UI —
 * [DnsScheduleWorker] and the settings screen call [syncCache] whenever
 * anything changes.
 *
 * Precision model: an exact (doze-aware) alarm at each window boundary +
 * the 15-minute periodic [DnsScheduleWorker] as a self-healing backstop that
 * recomputes desired state regardless of missed alarms/reboots.
 */
object PrivateDnsScheduler {

    private const val PREFS = "dns_auto_sched_cache"
    private const val C_ENABLED = "enabled"
    private const val C_START_MIN = "start_min"
    private const val C_END_MIN = "end_min"
    private const val C_HOST = "host"
    private const val C_DAY_MASK = "day_mask"
    private const val C_PAUSE_UNTIL = "pause_until"

    fun cache(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun syncCache(
        context: Context,
        enabled: Boolean,
        startMin: Int,
        endMin: Int,
        host: String,
        dayMask: Int = 0b1111111,
        pauseUntilMs: Long = 0L
    ) {
        cache(context).edit()
            .putBoolean(C_ENABLED, enabled)
            .putInt(C_START_MIN, startMin)
            .putInt(C_END_MIN, endMin)
            .putString(C_HOST, host)
            .putInt(C_DAY_MASK, dayMask)
            .putLong(C_PAUSE_UNTIL, pauseUntilMs)
            .apply()
    }

    data class Cache(
        val enabled: Boolean,
        val startMin: Int,
        val endMin: Int,
        val host: String,
        val dayMask: Int = 0b1111111,
        val pauseUntilMs: Long = 0L
    )

    fun readCache(context: Context): Cache {
        val p = cache(context)
        return Cache(
            p.getBoolean(C_ENABLED, false),
            p.getInt(C_START_MIN, 20 * 60),
            p.getInt(C_END_MIN, 8 * 60),
            p.getString(C_HOST, "") ?: "",
            p.getInt(C_DAY_MASK, 0b1111111),
            p.getLong(C_PAUSE_UNTIL, 0L)
        )
    }

    /** Overnight-safe window test: 20:00→08:00 wraps midnight (start > end). */
    fun isInWindow(nowMin: Int, startMin: Int, endMin: Int): Boolean =
        if (startMin > endMin) (nowMin >= startMin || nowMin < endMin)
        else if (startMin < endMin) (nowMin in startMin until endMin)
        else false

    /** Minutes-of-day right now. */
    fun nowMinutes(now: Calendar = Calendar.getInstance()): Int =
        now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

    /** R8 — day index matching the rest of the app: 0=Sun .. 6=Sat. */
    fun dayIndex(now: Calendar = Calendar.getInstance()): Int =
        (now.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)

    /** R8 — true while a 15-minute pause is active. */
    fun isPaused(pauseUntilMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        pauseUntilMs > nowMs

    /**
     * R8 — effective "DNS should be filtered RIGHT NOW": inside the time
     * window AND today is a scheduled day AND not paused. Overnight spillover
     * (02:00 inside a 20:00→08:00 window) belongs to YESTERDAY's day, so a
     * Friday night window keeps filtering into Saturday morning.
     */
    fun isEffectiveNow(
        nowMin: Int,
        startMin: Int,
        endMin: Int,
        dayMask: Int,
        pauseUntilMs: Long,
        now: Calendar = Calendar.getInstance()
    ): Boolean {
        if (isPaused(pauseUntilMs, now.timeInMillis)) return false
        if (!isInWindow(nowMin, startMin, endMin)) return false
        val today = dayIndex(now)
        val dayForWindow =
            if (startMin > endMin && nowMin < endMin) (today + 6) % 7 else today
        return dayMask and (1 shl dayForWindow) != 0
    }

    /**
     * Millis of the NEXT boundary (window start or end) strictly after [now],
     * or 0 when the feature is disabled. R8: an active pause adds one extra
     * boundary at its expiry so filtering resumes exactly on time.
     */
    fun nextBoundaryMillis(c: Cache, now: Calendar = Calendar.getInstance()): Long {
        if (!c.enabled || c.host.isBlank()) return 0L
        val candidates = listOf(c.startMin, c.endMin)
            .flatMap { m -> listOf(atMinutes(now, m, 0), atMinutes(now, m, 1)) }
            .filter { it > now.timeInMillis }
            .toMutableList()
        if (isPaused(c.pauseUntilMs, now.timeInMillis)) candidates += c.pauseUntilMs
        return candidates.minOrNull() ?: 0L
    }

    private fun atMinutes(now: Calendar, minutesOfDay: Int, plusDays: Int): Long {
        val cal = now.clone() as Calendar
        cal.add(Calendar.DAY_OF_YEAR, plusDays)
        cal.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
        cal.set(Calendar.MINUTE, minutesOfDay % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            7423,
            Intent(context, PrivateDnsReceiver::class.java).setAction(PrivateDnsReceiver.ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Arm (or cancel) the next boundary alarm from the cached schedule. */
    fun reschedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        val c = readCache(context)
        val next = nextBoundaryMillis(c)
        if (next <= 0L) {
            am.cancel(pi)
            Timber.d("PrivateDns: alarm cancelled (disabled or no host)")
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // User hasn't granted exact alarms on 31/32 — inexact still
                // lands on time outside doze; the periodic worker backstops.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            }
            Timber.d("PrivateDns: next boundary at $next (in ${(next - System.currentTimeMillis()) / 1000}s)")
        }.onFailure { Timber.e(it, "PrivateDns: alarm schedule failed") }
    }
}
