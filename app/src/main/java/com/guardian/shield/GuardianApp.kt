package com.guardian.shield

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorkerFactory
import com.guardian.shield.service.blocker.GuardianForegroundService
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class GuardianApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        createNotificationChannels()
        scheduleWatchdog()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_GUARDIAN,
                "Guardian Shield",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Persistent protection notification"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun scheduleWatchdog() {
        try {
            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(Constraints.Builder().build()).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WATCHDOG_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.d("Watchdog scheduled")
        } catch (t: Throwable) {
            Timber.e(t, "Failed to schedule watchdog")
        }
    }

    companion object {
        const val CHANNEL_GUARDIAN = "guardian_channel"
        const val WATCHDOG_WORK_NAME = "guardian_watchdog"
    }
}

class ServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            GuardianForegroundService.start(applicationContext)
            Timber.d("Watchdog: service pinged")
            Result.success()
        } catch (t: Throwable) {
            Timber.e(t, "Watchdog failed")
            Result.retry()
        }
    }
}