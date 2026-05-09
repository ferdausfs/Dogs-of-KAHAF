package com.kahaf.guardianshield.service.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.kahaf.guardianshield.service.timed.TimedBlockManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * AlarmManager-backed minute ticker. Wakes up roughly every minute and asks the
 * TimedBlockManager to recompute its blocked-package set. WorkManager covers the
 * Doze-friendly path; this gives us the tighter ≤60s response on the boundary.
 */
@AndroidEntryPoint
class MinuteTickReceiver : BroadcastReceiver() {

    @Inject lateinit var timedBlockManager: TimedBlockManager

    override fun onReceive(context: Context, intent: Intent) {
        try {
            timedBlockManager.recompute()
        } catch (t: Throwable) {
            Log.e(TAG, "onReceive error", t)
        } finally {
            schedule(context)
        }
    }

    companion object {
        private const val TAG = "MinuteTick"
        private const val REQUEST_CODE = 7137
        private const val PERIOD_MS = 60_000L

        fun schedule(context: Context) {
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, MinuteTickReceiver::class.java)
                val pi = PendingIntent.getBroadcast(
                    context, REQUEST_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerAt = SystemClock.elapsedRealtime() + PERIOD_MS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi
                    )
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "schedule failed", t)
            }
        }
    }
}
