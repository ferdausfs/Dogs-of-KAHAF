package com.guardianshield.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.guardianshield.app.R
import com.guardianshield.app.ui.MainActivity
import com.guardianshield.app.util.Constants

/** Keeps Guardian Shield resident so accessibility supervision is not killed by the OS. */
class ProtectionForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForeground(Constants.NOTIF_ID_PROTECTION, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, Constants.CHANNEL_PROTECTION)
            .setSmallIcon(R.drawable.ic_shield_24)
            .setContentTitle(getString(R.string.notif_protection_title))
            .setContentText(getString(R.string.notif_protection_text))
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
