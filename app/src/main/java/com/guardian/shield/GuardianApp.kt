package com.guardian.shield

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.material.color.DynamicColors
import com.guardian.shield.data.local.db.GuardianDatabase
import com.guardian.shield.service.blocker.GuardianForegroundService
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class GuardianApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        DynamicColors.applyToActivitiesIfAvailable(this)
        createNotificationChannels()
        scheduleWatchdog()
        scheduleLogCleanup()
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

    private fun scheduleLogCleanup() {
        try {
            val request = PeriodicWorkRequestBuilder<LogCleanupWorker>(
                1, TimeUnit.DAYS
            ).setConstraints(Constraints.Builder().build()).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                CLEANUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.d("Cleanup scheduled")
        } catch (t: Throwable) {
            Timber.e(t, "Failed to schedule cleanup")
        }
    }

    companion object {
        const val CHANNEL_GUARDIAN = "guardian_channel"
        const val WATCHDOG_WORK_NAME = "guardian_watchdog"
        const val CLEANUP_WORK_NAME = "guardian_cleanup"
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

class LogCleanupWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(45)
            val db = Room.databaseBuilder(
                applicationContext,
                GuardianDatabase::class.java,
                GuardianDatabase.DB_NAME
            )
                .addMigrations(
                    GuardianDatabase.MIGRATION_1_2,
                    GuardianDatabase.MIGRATION_2_3
                )
                .fallbackToDestructiveMigration()
                .build()

            val removed = try {
                db.blockEventDao().deleteOlderThan(cutoff)
            } finally {
                db.close()
            }
            Timber.d("Cleanup removed %d old events", removed)
            Result.success()
        } catch (t: Throwable) {
            Timber.e(t, "Cleanup failed")
            Result.retry()
        }
    }
}
