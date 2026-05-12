package com.guardian.shield.service.blocker

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.guardian.shield.GuardianApp
import com.guardian.shield.R
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.ui.dashboard.MainActivity
import com.guardian.shield.ui.guard.AccessibilityPromptActivity
import com.guardian.shield.util.GuardianConstants
import com.guardian.shield.util.Scopes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GuardianForegroundService : Service() {

    @Inject lateinit var prefs: GuardianPreferences

    private val serviceScope: CoroutineScope = Scopes.default()
    private var watchdogJob: Job? = null

    @Volatile private var protectionEnabled: Boolean = true
    private var consecutiveFailCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        startPrefsObserver()
        startAccessibilityWatchdog()
        return START_STICKY
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
        serviceScope.launch {
            try {
                prefs.protectionEnabled.collect { protectionEnabled = it }
            } catch (t: Throwable) {
                Timber.e(t, "protectionEnabled collector failed")
            }
        }
    }

    private fun startAccessibilityWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(GuardianConstants.ACCESSIBILITY_WATCHDOG_MS)
                try {
                    if (!protectionEnabled) {
                        consecutiveFailCount = 0
                        continue
                    }
                    if (!isAccessibilityEnabled()) {
                        consecutiveFailCount++
                        Timber.w("Accessibility check failed ($consecutiveFailCount/3)")
                        // ✅ 3 বার consecutive fail হলে তবেই prompt
                        // false positive এড়ানোর জন্য
                        if (consecutiveFailCount >= 3) {
                            Timber.w("Accessibility confirmed disabled. Showing prompt.")
                            launchAccessibilityPrompt()
                            consecutiveFailCount = 0
                        }
                    } else {
                        consecutiveFailCount = 0
                    }
                } catch (t: Throwable) {
                    Timber.e(t, "Watchdog error")
                    consecutiveFailCount = 0
                }
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            // ✅ Fix: simple contains check — package name থাকলেই enabled
            // আগে ভুল format ছিল: "$packageName/.service..." যেটা সবসময় false দিত
            enabledServices.contains(packageName, ignoreCase = true)
        } catch (_: Throwable) {
            true // error হলে assume enabled — prompt দেখাবে না
        }
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
    }

    companion object {
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, GuardianForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardianForegroundService::class.java))
        }
    }
}