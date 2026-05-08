package com.guardian.shield.service.blocker

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.guardian.shield.R
import com.guardian.shield.receiver.BootReceiver
import com.guardian.shield.service.accessibility.GuardianAccessibilityService
import com.guardian.shield.ui.dashboard.MainActivity
import com.guardian.shield.ui.permissions.PermissionsActivity
import com.guardian.shield.util.PermissionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * v8 FIX-LOG (stability pass):
 *  • BUG-05 → watchdog now also checks GuardianAccessibilityService.isRunning.
 *    On aggressive OEMs (MIUI / ColorOS) the OS can kill the bound service
 *    without disabling it in Settings, leaving Settings.isAccessibilityEnabled
 *    falsely returning true. We now post a separate degraded-state alert
 *    notification on the high-importance channel when this happens.
 *  • BUG-06 → onTaskRemoved no longer calls startForegroundService directly
 *    (which can race with process tear-down on Android 12+ →
 *    ForegroundServiceDidNotStartInTimeException). Instead we schedule a
 *    3-second AlarmManager wake-up to BootReceiver with a custom
 *    ACTION_RESTART_SERVICE.
 *  • BUG-14 → "degraded" notification now goes on the high-importance
 *    CHANNEL_ID_ALERT (separate notification ID), so it actually pops up
 *    as a heads-up. The persistent foreground notification stays unchanged
 *    on the low-importance CHANNEL_ID.
 */
@AndroidEntryPoint
class GuardianForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "guardian_protection"
        const val CHANNEL_ID_ALERT = "guardian_alerts"
        const val NOTIFICATION_ID = 4242
        const val ALERT_NOTIFICATION_ID = NOTIFICATION_ID + 1
        private const val WATCHDOG_INTERVAL_MS = 30_000L
        private const val SELF_RESTART_DELAY_MS = 3_000L

        fun start(ctx: Context) = runCatching {
            val intent = Intent(ctx, GuardianForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        }.onFailure { Timber.w(it, "GuardianForegroundService.start failed") }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchdogJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        val notif = buildForegroundNotification(missingCount = 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
        startWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * BUG-06: schedule a delayed self-restart through AlarmManager → BootReceiver
     * instead of calling startForegroundService synchronously. On Android 12+
     * the latter can throw ForegroundServiceDidNotStartInTimeException because
     * the process is already being torn down.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching {
            val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(applicationContext, BootReceiver::class.java).apply {
                action = BootReceiver.ACTION_RESTART_SERVICE
            }
            val pi = PendingIntent.getBroadcast(
                applicationContext,
                7373,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = SystemClock.elapsedRealtime() + SELF_RESTART_DELAY_MS
            if (am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                } else {
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
            }
        }.onFailure { Timber.w(it, "Self-restart alarm scheduling failed") }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        scope.cancel()
        // Clear any lingering alert notification on clean shutdown.
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(ALERT_NOTIFICATION_ID)
        }
        super.onDestroy()
    }

    // -------------------- watchdog --------------------

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                runCatching {
                    val ctx = this@GuardianForegroundService
                    val missing = PermissionManager.missingCritical(ctx)

                    // BUG-05: detect "settings says enabled but service is dead".
                    val accSettingsOn = PermissionManager.isAccessibilityEnabled(ctx)
                    val accReallyRunning = GuardianAccessibilityService.isRunning
                    val accDegraded = accSettingsOn && !accReallyRunning

                    val degradedCount = missing.size + (if (accDegraded) 1 else 0)

                    val nm = ctx.getSystemService(NotificationManager::class.java)
                    // Foreground notification stays on its own channel (BUG-14).
                    nm?.notify(NOTIFICATION_ID, buildForegroundNotification(degradedCount))

                    // Separate high-importance heads-up alert when degraded.
                    if (degradedCount > 0) {
                        nm?.notify(
                            ALERT_NOTIFICATION_ID,
                            buildAlertNotification(degradedCount, accDegraded)
                        )
                    } else {
                        nm?.cancel(ALERT_NOTIFICATION_ID)
                    }
                }.onFailure { Timber.w(it, "Watchdog tick failed") }
                delay(WATCHDOG_INTERVAL_MS)
            }
        }
    }

    // -------------------- notification --------------------

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Guardian Shield Protection", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Keeps Guardian Shield active" }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_ALERT, "Guardian Shield Alerts", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Alerts when protection is degraded" }
            )
        }
    }

    /** Persistent foreground notification (low importance, never makes a sound). */
    private fun buildForegroundNotification(missingCount: Int): Notification {
        val openMain = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPerms = PendingIntent.getActivity(
            this, 1, Intent(this, PermissionsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = getString(R.string.app_name)
        val text = if (missingCount == 0)
            getString(R.string.protection_active)
        else
            getString(R.string.protection_degraded_short, missingCount)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(if (missingCount == 0) openMain else openPerms)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * BUG-14: separate high-importance heads-up alert notification. Posted on
     * the alerts channel — IMPORTANCE_HIGH means it actually pops up and
     * makes a sound on Android 8+.
     */
    private fun buildAlertNotification(missingCount: Int, accessibilityDegraded: Boolean): Notification {
        val openPerms = PendingIntent.getActivity(
            this, 2, Intent(this, PermissionsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (accessibilityDegraded)
            getString(R.string.protection_degraded_accessibility)
        else
            getString(R.string.protection_degraded_long, missingCount)

        return NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.protection_degraded_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openPerms)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
    }
}
