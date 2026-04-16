package com.kahaf.guardian.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kahaf.guardian.R
import com.kahaf.guardian.ui.main.MainActivity
import com.kahaf.guardian.util.Constants
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KahafForegroundService : Service() {
    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, KahafForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, KahafForegroundService::class.java)) }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(Constants.NOTIFICATION_CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = getString(R.string.notification_channel_desc); setShowBadge(false) })
        }
        startForeground(Constants.NOTIFICATION_ID, NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW).build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
