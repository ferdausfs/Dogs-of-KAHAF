package com.kahaf.guardianshield.service.foreground

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.kahaf.guardianshield.GuardianShieldApp
import com.kahaf.guardianshield.R
import com.kahaf.guardianshield.domain.repository.AppRuleRepository
import com.kahaf.guardianshield.domain.repository.KeywordRepository
import com.kahaf.guardianshield.presentation.MainActivity
import com.kahaf.guardianshield.service.timed.TimedBlockManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Long-running foreground service used to keep the Accessibility pipeline alive
 * under Doze and aggressive OEM killers.
 *
 *  - foregroundServiceType="specialUse" (justified by <property> tag in manifest)
 *  - Notification is LOW priority + no-badge to stay unobtrusive
 *  - Updates the notification text whenever rule counts change
 *  - Kicks the TimedBlockManager ticker on creation
 */
@AndroidEntryPoint
class GuardianForegroundService : LifecycleService() {

    @Inject lateinit var appRuleRepository: AppRuleRepository
    @Inject lateinit var keywordRepository: KeywordRepository
    @Inject lateinit var timedBlockManager: TimedBlockManager

    override fun onCreate() {
        super.onCreate()
        startInForeground(0, 0)
        timedBlockManager.startTicker()

        try {
            appRuleRepository.observeBlockedPackages()
                .combine(keywordRepository.observeAll()) { blocked, kws -> blocked.size to kws.size }
                .onEach { (b, k) -> updateNotification(b, k) }
                .launchIn(lifecycleScope)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed wiring rule counters", t)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startInForeground(blocked: Int, keywords: Int) {
        val notif = buildNotification(blocked, keywords)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID, notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
        }
    }

    private fun updateNotification(blocked: Int, keywords: Int) {
        val nm = androidx.core.app.NotificationManagerCompat.from(this)
        try {
            nm.notify(NOTIF_ID, buildNotification(blocked, keywords))
        } catch (_: SecurityException) {
            // Pre-Tiramisu user could revoke notification permission — ignore
        }
    }

    private fun buildNotification(blocked: Int, keywords: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, GuardianShieldApp.CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.fg_notif_title))
            .setContentText(getString(R.string.fg_notif_text, blocked, keywords))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent)
            .build()
    }

    companion object {
        private const val TAG = "GuardianFgService"
        private const val NOTIF_ID = 1042

        fun start(context: Context) {
            try {
                val intent = Intent(context, GuardianForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "start() failed", t)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, GuardianForegroundService::class.java))
            } catch (_: Throwable) {}
        }
    }
}
