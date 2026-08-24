package com.guardian.shield.service.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.guardian.shield.data.local.datastore.GuardianPreferences
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Calendar

/**
 * R7.5 — Bedtime Mode: a NIGHTLY scheduled Focus. Same proven plumbing as
 * Private DNS Auto Mode (SharedPreferences sync cache + exact window-boundary
 * alarm + periodic worker backstop), but the appliance target is
 * [GuardianPreferences.focusUntilMs] — the RulesEngine already blocks every
 * distracting app while focusUntil > now, so NO engine changes are needed.
 *
 * Semantics:
 *  - Inside the window, if the current focus would end BEFORE the window
 *    does, the focus until-stamp is extended to the window end. A longer
 *    manual focus session is never shortened.
 *  - The window-end tick needs no OFF action: the focus stamp expires on its
 *    own (RulesEngine compares against now).
 *  - Switching Bedtime OFF mid-window clears the focus stamp only when it is
 *    exactly the one Bedtime applied (never kills a manual session).
 */
object BedtimeScheduler {

    private const val PREFS = "bedtime_sched_cache"
    private const val C_ENABLED = "enabled"
    private const val C_START_MIN = "start_min"
    private const val C_END_MIN = "end_min"
    private const val C_LAST_APPLIED = "last_applied_until"

    fun cache(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun syncCache(context: Context, enabled: Boolean, startMin: Int, endMin: Int) {
        cache(context).edit()
            .putBoolean(C_ENABLED, enabled)
            .putInt(C_START_MIN, startMin)
            .putInt(C_END_MIN, endMin)
            .apply()
    }

    data class Cache(val enabled: Boolean, val startMin: Int, val endMin: Int)

    fun readCache(context: Context): Cache {
        val p = cache(context)
        return Cache(
            p.getBoolean(C_ENABLED, false),
            p.getInt(C_START_MIN, 23 * 60),
            p.getInt(C_END_MIN, 6 * 60)
        )
    }

    /** Overnight-safe window test (23:00→06:00 wraps midnight). */
    fun isInWindow(nowMin: Int, startMin: Int, endMin: Int): Boolean =
        if (startMin > endMin) (nowMin >= startMin || nowMin < endMin)
        else if (startMin < endMin) (nowMin in startMin until endMin)
        else false

    fun nowMinutes(now: Calendar = Calendar.getInstance()): Int =
        now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

    /** Wall-clock end of the window instance containing [now]. */
    fun windowEndTs(c: Cache, now: Calendar = Calendar.getInstance()): Long {
        val nowMin = nowMinutes(now)
        val endToday = atMinutes(now, c.endMin, 0)
        // Overnight + after start => the end belongs to TOMORROW morning.
        return if (c.startMin > c.endMin && nowMin >= c.startMin) {
            atMinutes(now, c.endMin, 1)
        } else endToday
    }

    /**
     * Enforce the desired focus state for right now. Safe to call from the
     * tick receiver, the periodic worker, and the UI after every edit.
     */
    suspend fun tick(context: Context, prefs: GuardianPreferences) {
        val c = readCache(context)
        if (!c.enabled) return
        val now = Calendar.getInstance()
        if (!isInWindow(nowMinutes(now), c.startMin, c.endMin)) return
        val endTs = windowEndTs(c, now)
        val current = runCatching { prefs.focusUntilMs.first() }.getOrDefault(0L)
        if (current < endTs) {
            prefs.setFocusUntilMs(endTs)
            prefs.bumpRulesVersion()
            cache(context).edit().putLong(C_LAST_APPLIED, endTs).apply()
            Timber.i("Bedtime: focus extended until $endTs")
        }
    }

    /** Clear an in-flight bedtime focus stamp when the user switches off. */
    suspend fun disableNow(context: Context, prefs: GuardianPreferences) {
        val last = cache(context).getLong(C_LAST_APPLIED, 0L)
        if (last <= 0L) return
        val current = runCatching { prefs.focusUntilMs.first() }.getOrDefault(0L)
        if (current == last) {
            prefs.setFocusUntilMs(0L)
            prefs.bumpRulesVersion()
            Timber.i("Bedtime: own focus stamp cleared on disable")
        }
        cache(context).edit().putLong(C_LAST_APPLIED, 0L).apply()
    }

    /** Millis of the NEXT boundary (window start or end) strictly after now. */
    fun nextBoundaryMillis(c: Cache, now: Calendar = Calendar.getInstance()): Long {
        if (!c.enabled || c.startMin == c.endMin) return 0L
        val candidates = listOf(c.startMin, c.endMin)
            .flatMap { m -> listOf(atMinutes(now, m, 0), atMinutes(now, m, 1)) }
            .filter { it > now.timeInMillis }
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
            7424,
            Intent(context, BedtimeReceiver::class.java).setAction(BedtimeReceiver.ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Arm (or cancel) the next window-boundary alarm from the cache. */
    fun reschedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        val next = nextBoundaryMillis(readCache(context))
        if (next <= 0L) {
            am.cancel(pi)
            Timber.d("Bedtime: alarm cancelled (disabled)")
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            }
            Timber.d("Bedtime: next boundary in ${(next - System.currentTimeMillis()) / 1000}s")
        }.onFailure { Timber.e(it, "Bedtime: alarm schedule failed") }
    }
}
