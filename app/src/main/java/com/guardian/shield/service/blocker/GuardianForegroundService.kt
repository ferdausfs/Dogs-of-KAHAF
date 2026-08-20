package com.guardian.shield.service.blocker

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.guardian.shield.GuardianApp
import com.guardian.shield.R
import com.guardian.shield.admin.GuardianDeviceAdminReceiver
import com.guardian.shield.admin.TamperLogger
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.ui.dashboard.MainActivity
import com.guardian.shield.ui.guard.AccessibilityPromptActivity
import com.guardian.shield.util.AccessibilityHeartbeat
import com.guardian.shield.util.GuardianConstants
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
import javax.inject.Inject

@AndroidEntryPoint
class GuardianForegroundService : Service() {

    @Inject lateinit var prefs: GuardianPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchdogJob: Job? = null
    // STABILITY FIX — the service can be (re)started many times (boot, device
    // admin, watchdog worker every 15 min, MainActivity). Each start used to
    // launch a NEW never-ending pref collector without cancelling the old one,
    // leaking a coroutine per start. Keep a single observer job and replace it.
    private var prefsObserverJob: Job? = null

    @Volatile private var protectionEnabled: Boolean = true
    private var consecutiveFailCount = 0

    // PHASE 1a (v3.5.0) — device-admin revocation watch. Solely *detection /
    // re-prompt* state; it never changes how protection decisions are made.
    // null = unknown (first tick), true/false = last observed admin state.
    @Volatile private var adminActiveLast: Boolean? = null
    @Volatile private var lastAdminNotifyAt: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        startPrefsObserver()
        startAccessibilityWatchdog()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.w("Task removed — restarting")
        val restart = Intent(applicationContext, GuardianForegroundService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(restart)
            else startService(restart)
        }
    }

    private fun startForegroundCompat() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun startPrefsObserver() {
        prefsObserverJob?.cancel()
        prefsObserverJob = serviceScope.launch {
            try { prefs.protectionEnabled.collect { protectionEnabled = it } }
            catch (t: Throwable) { Timber.e(t) }
        }
    }

    private fun startAccessibilityWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(GuardianConstants.ACCESSIBILITY_WATCHDOG_MS)
                try {
                    if (!protectionEnabled) { consecutiveFailCount = 0; continue }
                    val enabled = isAccessibilityEnabled()
                    // Liveness: the settings toggle can stay "on" even after the
                    // service dies. Treat a stale heartbeat as "down" so the user
                    // is re-prompted instead of silently unprotected.
                    val stale = AccessibilityHeartbeat.sinceLastBeatMs() > GuardianConstants.ACCESSIBILITY_STALE_MS
                    if (!enabled || stale) {
                        consecutiveFailCount++
                        Timber.w("Accessibility fail $consecutiveFailCount/3 (enabled=$enabled, stale=$stale)")
                        if (consecutiveFailCount >= 3) {
                            // PHASE 1a (v3.5.0) — record the disable/kill durably
                            // (tamper_log.txt + high-priority notification). Purely
                            // observational: the prompt flow below is unchanged.
                            TamperLogger.log(this@GuardianForegroundService, "accessibility-disabled")
                            launchAccessibilityPrompt()
                            consecutiveFailCount = 0
                        }
                    } else {
                        consecutiveFailCount = 0
                    }
                    checkDeviceAdminWatch()
                } catch (t: Throwable) {
                    Timber.e(t, "Watchdog error")
                    consecutiveFailCount = 0
                }
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabled.contains(packageName, ignoreCase = true)
        } catch (_: Throwable) { false }
    }

    private fun launchAccessibilityPrompt() {
        runCatching {
            startActivity(
                Intent(this, AccessibilityPromptActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }

    // ---------------------------------------------------------------------
    // PHASE 1a (v3.5.0) — Device Admin revoked re-request watchdog.
    //
    // Previously, an admin revocation WITHOUT an active Commitment Lock was
    // only noticed in two places: GuardianDeviceAdminReceiver.onDisabled()
    // (which re-requests ONLY when a lock/cooldown is active) and the next
    // MainActivity open (checkDeviceAdmin dialog). That left a real gap: the
    // user could revoke Device Admin and the app would keep running as if
    // uninstall-protection were intact until the app was next opened.
    //
    // This watch runs in the same 5s watchdog loop as the accessibility
    // liveness check. It is DETECTION + PROMPT ONLY:
    //   - active -> revoked transition: TamperLogger.log("device-admin-revoked")
    //     (durable tamper_log.txt entry + existing tamper notification) and a
    //     dedicated high-priority notification whose tap directly reopens the
    //     ACTION_ADD_DEVICE_ADMIN prompt.
    //   - still revoked: re-notify at most every DEVICE_ADMIN_RECHECK_MS.
    //   - revoked -> active: state resets silently.
    // It adds no blocking decisions and touches no detection logic.
    // ---------------------------------------------------------------------
    private fun isDeviceAdminActive(): Boolean = runCatching {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.isAdminActive(ComponentName(this, GuardianDeviceAdminReceiver::class.java))
    }.getOrDefault(false)

    private fun checkDeviceAdminWatch() {
        val now = System.currentTimeMillis()
        val active = isDeviceAdminActive()
        val last = adminActiveLast
        adminActiveLast = active
        val justRevoked = (last == true && !active)
        val recheckDue = !active &&
            (now - lastAdminNotifyAt) >= GuardianConstants.DEVICE_ADMIN_RECHECK_MS
        if (justRevoked || (last != null && recheckDue)) {
            lastAdminNotifyAt = now
            Timber.w("Device Admin revoked while protection enabled — prompting re-grant")
            TamperLogger.log(this, "device-admin-revoked")
            notifyDeviceAdminRevoked()
        } else if (last == null && !active) {
            // First tick with admin missing: prompt once without a tamper entry
            // (the admin may simply never have been granted).
            lastAdminNotifyAt = now
            notifyDeviceAdminRevoked()
        }
    }

    private fun notifyDeviceAdminRevoked() {
        runCatching {
            val admin = ComponentName(this, GuardianDeviceAdminReceiver::class.java)
            val reEnable = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.admin_revoked_notif_text)
                )
            }
            val pi = PendingIntent.getActivity(
                this, NOTIF_ID_ADMIN,
                reEnable,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(this, GuardianApp.CHANNEL_GUARDIAN)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle(getString(R.string.admin_revoked_notif_title))
                .setContentText(getString(R.string.admin_revoked_notif_text))
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()
            (getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager)
                ?.notify(NOTIF_ID_ADMIN, n)
        }.onFailure { Timber.w(it, "Device-admin-revoked notification failed") }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, GuardianApp.CHANNEL_GUARDIAN)
            .setSmallIcon(R.drawable.ic_shield_on)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        watchdogJob?.cancel()
        prefsObserverJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIF_ID = 1001
        // PHASE 1a (v3.5.0) — dedicated id for the device-admin-revoked prompt.
        private const val NOTIF_ID_ADMIN = 1004

        fun start(context: Context) {
            val intent = Intent(context, GuardianForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardianForegroundService::class.java))
        }
    }
}