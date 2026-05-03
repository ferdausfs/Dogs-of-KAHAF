package com.guardian.shield.service.blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.guardian.shield.R
import com.guardian.shield.ui.dashboard.MainActivity
import timber.log.Timber

/**
 * Persistent foreground service — keeps the app process alive so the
 * AccessibilityService isn't killed by the OS under memory pressure.
 */
class GuardianForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID      = "guardian_protection"
        private const val NOTIFICATION_ID = 1001
        private const val TAG             = "Guardian_Foreground"
        const val ACTION_RESTART          = "com.guardian.shield.RESTART_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("$TAG onCreate")
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // FIX: START_REDELIVER_INTENT removed — combined with onDestroy broadcast
        // caused double restart. START_STICKY is sufficient.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Timber.d("$TAG onDestroy — sending restart broadcast")
        try {
            sendBroadcast(Intent(ACTION_RESTART).apply {
                setPackage(packageName)
            })
        } catch (e: Exception) {
            Log.e(TAG, "restart broadcast failed: ${e.message}")
        }
        super.onDestroy()
    }

    // FIX: minSdk=26, NotificationChannel always available — removed API check
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Guardian Protection",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Active content protection"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guardian Shield")
            .setContentText("Protection is active")
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }
}