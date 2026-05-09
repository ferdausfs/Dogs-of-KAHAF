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
import com.guardian.shield.util.Scopes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * v12 (2.1.2):
 *  • DEFENSIVE: createChannels() return-early on null NotificationManager.
 *  • Watchdog tick interval increased to 45s (from 30s) — saves battery
 *    and is enough granularity for permission-degraded detection.
 *  • Notification PendingIntent flags are now centralised constants.
 *
 * v11 (2.1.1):
 *  • startForegroundSafely catches every Throwable.
 *  • scheduleRetryAlarm fallback when foreground promotion is denied.
 *  • POST_NOTIFICATIONS handled at Activity level.
 */
@AndroidEntryPoint
class GuardianForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "guardian_protection"
        const val CHANNEL_ID_ALERT = "guardian_alerts"
        const val NOTIFICATION_ID = 4242
        const val ALERT_NOTIFICATION_ID = NOTIFICATION_ID + 1
        private const val WATCHDOG_INTERVAL_MS = 45_000L     // v12: 30s → 45s
        private const val SELF_RESTART_DELAY_MS = 3_000L

        private const val PI_FLAGS =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        fun start(ctx: Context) {
            runCatching {
                val intent = Intent(ctx, GuardianForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
            }.onFailure {
                Timber.w(it, "GuardianForegroundService.start failed — will be retried by alarm")
                scheduleRetryAlarm(ctx)
            }
        }

        private fun scheduleRetryAlarm(ctx: Context) {
            runCatching {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(ctx.applicationContext, BootReceiver::class.java).apply {
                    action = BootReceiver.ACTION_RESTART_SERVICE
                }
                val pi = PendingIntent.getBroadcast(ctx.applicationContext, 7374, intent, PI_FLAGS)
                val triggerAt = SystemClock.elapsedRealtime() + SELF_RESTART_DELAY_MS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                } else {
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
            }.onFailure { Timber.w(it, "scheduleRetryAlarm failed (suppressed)") }
        }
    }

    private val scope: CoroutineScope = Scopes.default()
    private var watchdogJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        runCatching { createChannels() }.onFailure {
            Timber.e(it, "createChannels failed — service will likely fail to start")
        }

        val notif = buildForegroundNotification(missingCount = 0)
        val started = startForegroundSafely(notif)
        if (!started) {
            Timber.w("startForegroundSafely returned false — stopping self, retry will be scheduled")
            runCatching { scheduleRetryAlarm(applicationContext) }
            stopSelf()
            return
        }
        startWatchdog()
    }

    private fun startForegroundSafely(notif: Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, notif,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
            true
        } catch (t: Throwable) {
            Timber.e(t, "startForeground rejected: ${t.javaClass.simpleName}")
            false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching {
            scheduleRetryAlarm(applicationContext)
        }.onFailure { Timber.w(it, "onTaskRemoved retry alarm failed") }
        try {
            super.onTaskRemoved(rootIntent)
        } catch (t: Throwable) {
            Timber.w(t, "super.onTaskRemoved threw — suppressed")
        }
    }

    override fun onDestroy() {
        runCatching { watchdogJob?.cancel() }
        runCatching { scope.cancel() }
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(ALERT_NOTIFICATION_ID)
        }
        try {
            super.onDestroy()
        } catch (t: Throwable) {
            Timber.w(t, "super.onDestroy threw — suppressed")
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                runCatching {
                    val ctx = this@GuardianForegroundService
                    // v15 (2.1.5) HARD-1: each system-service call wrapped
                    // individually so a single hung binder thread can't
                    // poison the rest of the watchdog tick.
                    val missing = runCatching { PermissionManager.missingCritical(ctx) }
                        .getOrDefault(emptyList())

                    val accSettingsOn = runCatching { PermissionManager.isAccessibilityEnabled(ctx) }
                        .getOrDefault(false)
                    val accReallyRunning = GuardianAccessibilityService.isRunning
                    val accDegraded = accSettingsOn && !accReallyRunning

                    val degradedCount = missing.size + (if (accDegraded) 1 else 0)

                    val nm = ctx.getSystemService(NotificationManager::class.java)
                    runCatching {
                        nm?.notify(NOTIFICATION_ID, buildForegroundNotification(degradedCount))
                    }.onFailure { Timber.w(it, "watchdog notify foreground failed") }

                    if (degradedCount > 0) {
                        runCatching {
                            nm?.notify(
                                ALERT_NOTIFICATION_ID,
                                buildAlertNotification(degradedCount, accDegraded)
                            )
                        }.onFailure { Timber.w(it, "watchdog notify alert failed") }
                    } else {
                        runCatching { nm?.cancel(ALERT_NOTIFICATION_ID) }
                    }
                }.onFailure { Timber.w(it, "Watchdog tick failed") }
                delay(WATCHDOG_INTERVAL_MS)
            }
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Guardian Shield Protection", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Keeps Guardian Shield active" }
            )
        }
        runCatching {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_ALERT, "Guardian Shield Alerts", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Alerts when protection is degraded" }
            )
        }
    }

    private fun buildForegroundNotification(missingCount: Int): Notification {
        val openMain = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PI_FLAGS
        )
        val openPerms = PendingIntent.getActivity(
            this, 1, Intent(this, PermissionsActivity::class.java), PI_FLAGS
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

    private fun buildAlertNotification(missingCount: Int, accessibilityDegraded: Boolean): Notification {
        val openPerms = PendingIntent.getActivity(
            this, 2, Intent(this, PermissionsActivity::class.java), PI_FLAGS
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
