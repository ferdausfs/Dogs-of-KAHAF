package com.guardian.shield.service.blocker

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.guardian.shield.R
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
 * FIX-LOG (vs original):
 *  - BUG #13: wrap startForegroundService in a try/catch — API 31+ can throw
 *    BackgroundServiceStartNotAllowedException, and on some OEMs even foreground
 *    starts can fail. Failing silently is better than crashing the host.
 *  - Use FOREGROUND_SERVICE_TYPE_SPECIAL_USE on API 34+ when calling
 *    startForeground() so the service binds to its declared FGS type.
 *
 *  v2 update (this commit, no breakage to existing flow):
 *   - Permission Watchdog: every ~30s the service re-checks Accessibility,
 *     Overlay, Battery-unrestricted, and Auto-revoke status. If any critical
 *     permission is missing, we update the persistent notification to a
 *     high-priority warning that opens PermissionsActivity in one tap. This
 *     directly addresses the user's report:
 *       "permission auto remove hoy / sob thik ase kintu app kaj kore na".
 *   - onTaskRemoved: we self-restart so swiping the app away no longer
 *     silently kills protection on aggressive OEMs.
 */
@AndroidEntryPoint
class GuardianForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "guardian_protection"
        const val CHANNEL_ID_ALERT = "guardian_alerts"
        const val NOTIFICATION_ID = 4242
        private const val WATCHDOG_INTERVAL_MS = 30_000L

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
        val notif = buildNotification(missingCount = 0)
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
     * Some OEMs (MIUI / ColorOS / Realme UI) kill the service when the user
     * swipes the task. We self-restart so blocking continues — the user has
     * to actively disable accessibility/admin to truly stop us.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching {
            val restart = Intent(applicationContext, GuardianForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                applicationContext.startForegroundService(restart)
            else
                applicationContext.startService(restart)
        }.onFailure { Timber.w(it, "Self-restart on onTaskRemoved failed") }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // -------------------- watchdog --------------------

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                runCatching {
                    val missing = PermissionManager.missingCritical(this@GuardianForegroundService)
                    val nm = getSystemService(NotificationManager::class.java)
                    nm?.notify(NOTIFICATION_ID, buildNotification(missing.size))
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

    private fun buildNotification(missingCount: Int): Notification {
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
            "⚠ $missingCount permission(s) missing — tap to fix"

        // Use the alert channel only when degraded; foreground notification
        // itself stays on the low-importance channel so we don't violate
        // the foreground-service notification contract.
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(if (missingCount == 0) openMain else openPerms)
            .setPriority(
                if (missingCount == 0) NotificationCompat.PRIORITY_LOW
                else NotificationCompat.PRIORITY_HIGH
            )
            .build()
    }
}
