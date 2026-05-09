package com.kahaf.guardianshield

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Guardian Shield Application entry-point.
 *
 * - Annotated @HiltAndroidApp so Hilt generates the application-scoped component.
 * - Implements Configuration.Provider to wire HiltWorkerFactory into WorkManager
 *   (we disabled the default WorkManagerInitializer in the manifest).
 * - Creates the foreground service notification channel up-front so we don't have
 *   a race when the AccessibilityService starts the foreground service.
 */
@HiltAndroidApp
class GuardianShieldApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_FOREGROUND,
            getString(R.string.fg_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.fg_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_FOREGROUND = "guardian_shield_fg"
    }
}
