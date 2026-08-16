package com.guardian.shield.admin

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.guardian.shield.GuardianApp
import com.guardian.shield.R
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Phase 5 — Lightweight tampering logger / notifier.
 *
 * We deliberately avoid touching the Room DB here so that — even if a future
 * tamper attempt triggers a crash mid-block — the *notification* still fires
 * and the parent learns about it. The most important guarantee is that this
 * is best-effort and never throws.
 *
 * In addition to the high-priority notification, every attempt is appended to
 * a durable, crash-safe log file so there is a persistent record that cannot
 * be dismissed with the notification.
 */
object TamperLogger {

    fun log(context: Context, attemptType: String) {
        Timber.w("Tamper attempt: $attemptType")
        persist(context, attemptType)
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as? NotificationManager ?: return
            val n = NotificationCompat.Builder(context, GuardianApp.CHANNEL_GUARDIAN)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle(context.getString(R.string.tamper_notif_title))
                .setContentText(context.getString(R.string.tamper_notif_text, attemptType))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()
            nm.notify(TAMPER_NOTIF_ID + (attemptType.hashCode() and 0xFFFF), n)
        } catch (t: Throwable) {
            Timber.w(t, "Tamper notify failed")
        }
    }

    /** Append a "timestamp|type" record to a best-effort, crash-safe log file. */
    private fun persist(context: Context, attemptType: String) {
        synchronized(this) {
            try {
                val f = File(context.filesDir, TAMPER_LOG_FILE)
                val line = "${System.currentTimeMillis()}|$attemptType\n"
                FileOutputStream(f, true).use { it.write(line.toByteArray(Charsets.UTF_8)) }
            } catch (t: Throwable) {
                Timber.w(t, "Tamper log append failed")
            }
        }
    }

    private const val TAMPER_NOTIF_ID = 9000
    private const val TAMPER_LOG_FILE = "tamper_log.txt"
}
