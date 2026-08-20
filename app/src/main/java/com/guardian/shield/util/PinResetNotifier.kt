package com.guardian.shield.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.guardian.shield.GuardianApp
import com.guardian.shield.R
import com.guardian.shield.data.local.datastore.SecureStorage
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.ui.setup.PinRecoveryActivity
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * PHASE 1c (v3.5.0) — notifications for the time-delayed PIN reset.
 *
 * A timed reset is only safe as a commitment mechanism if it stays LOUD the
 * whole 48 hours: an ongoing, non-auto-cancel notification shows the exact
 * deadline for the entire waiting window, and a second notification fires
 * when the wait completes. Tapping either opens [PinRecoveryActivity].
 *
 * The content text carries the fixed deadline timestamp (not a live
 * countdown) so no periodic refresh work is needed; the "ready" alert is
 * delivered by [PinResetAlertWorker], scheduled for the deadline.
 */
object PinResetNotifier {

    const val NOTIF_ID_PENDING = 1005
    const val NOTIF_ID_READY = 1006
    private const val WORK_NAME = "pin_reset_alert"

    private fun openRecoveryIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, PinRecoveryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Ongoing "reset in progress" alert showing the fixed deadline. */
    fun showPending(context: Context, deadlineAt: Long) {
        runCatching {
            val deadline = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(deadlineAt))
            val n = NotificationCompat.Builder(context, GuardianApp.CHANNEL_GUARDIAN)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle(context.getString(R.string.pin_reset_pending_notif_title))
                .setContentText(context.getString(R.string.pin_reset_pending_notif_text, deadline))
                .setContentIntent(openRecoveryIntent(context))
                .setOngoing(true)          // stays visible for the whole wait
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.notify(NOTIF_ID_PENDING, n)
        }.onFailure { Timber.w(it, "showPending notification failed") }
    }

    /** High-priority "the wait is over — reset is now possible" alert. */
    fun showReady(context: Context) {
        runCatching {
            val n = NotificationCompat.Builder(context, GuardianApp.CHANNEL_GUARDIAN)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle(context.getString(R.string.pin_reset_ready_notif_title))
                .setContentText(context.getString(R.string.pin_reset_ready_notif_text))
                .setContentIntent(openRecoveryIntent(context))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.notify(NOTIF_ID_READY, n)
        }.onFailure { Timber.w(it, "showReady notification failed") }
    }

    fun cancelAll(context: Context) {
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.apply {
                    cancel(NOTIF_ID_PENDING)
                    cancel(NOTIF_ID_READY)
                }
        }
    }

    /** Schedule the one-shot worker that fires exactly at the reset deadline. */
    fun scheduleReadyAlert(context: Context, deadlineAt: Long) {
        val delayMs = (deadlineAt - System.currentTimeMillis()).coerceAtLeast(0L)
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PinResetAlertWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(Data.Builder().build())
                    .build()
            )
        }.onFailure { Timber.w(it, "scheduleReadyAlert failed") }
    }

    fun cancelReadyAlert(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
    }
}

/**
 * Fires when the 48h PIN-reset wait elapses (scheduled by
 * [PinResetNotifier.scheduleReadyAlert]). If the request is still pending and
 * the delay has truly elapsed, posts the "reset ready" notification and drops
 * the ongoing "pending" one. Plain Worker — PinManager is constructed
 * directly over SecureStorage (no Hilt graph needed).
 */
class PinResetAlertWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result = try {
        val pinManager = PinManager(SecureStorage(applicationContext))
        if (pinManager.timedResetRequestedAt() > 0L && pinManager.isTimedResetReady()) {
            PinResetNotifier.showReady(applicationContext)
            runCatching {
                (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                    as? NotificationManager)?.cancel(PinResetNotifier.NOTIF_ID_PENDING)
            }
            Timber.w("PIN timed-reset wait elapsed — ready notification posted")
        } else {
            Timber.d("PIN reset alert fired but no ready pending reset — ignored")
        }
        Result.success()
    } catch (t: Throwable) {
        Timber.e(t, "PinResetAlertWorker failed")
        Result.retry()
    }
}
