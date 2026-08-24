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
import com.guardian.shield.service.blocker.PendingReportManager
import com.guardian.shield.util.GuardianCrashHandler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class GuardianApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // PHASE 2 (v3.5.0) — accountability partner observer/notifier.
    @Inject lateinit var accountabilityNotifier: com.guardian.shield.accountability.AccountabilityNotifier

    // v3.6.1 — re-enqueue cooling-off workers after process start / reboot.
    @Inject lateinit var pendingReportManager: PendingReportManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // PHASE 1b (v3.5.0) — crash reporting: local log always; Crashlytics
        // only when google-services.json was present at build time.
        GuardianCrashHandler.install(this)
        createNotificationChannels()
        scheduleWatchdog()
        // PHASE 2 (v3.5.0) — begin observing accountability events (partner
        // contact is optional; nothing happens until one is configured).
        runCatching { accountabilityNotifier.start() }
            .onFailure { Timber.e(it, "AccountabilityNotifier start failed") }
        // WorkManager persists work across reboots, but an explicit
        // reschedule covers unique-work loss after a custom
        // Configuration.Provider and clock-skewed initialDelay.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { pendingReportManager.rescheduleAllPending() }
                .onFailure { Timber.e(it, "Failed to reschedule pending cooling-off reports") }
        }
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

            // R5 — Private DNS Auto Mode: periodic self-healer (syncs the
            // schedule cache, enforces current desired state, re-arms the
            // exact boundary alarm). UPDATE so installs upgrading to the
            // release that introduced this worker pick it up immediately.
            val dnsRequest = PeriodicWorkRequestBuilder<com.guardian.shield.service.dns.DnsScheduleWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(Constraints.Builder().build()).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                DNS_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                dnsRequest
            )

            // R7.5 — Bedtime Mode: periodic self-healer (syncs the window
            // cache, enforces the scheduled focus state, re-arms the exact
            // boundary alarm).
            val bedtimeRequest = PeriodicWorkRequestBuilder<com.guardian.shield.service.focus.BedtimeWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(Constraints.Builder().build()).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                BEDTIME_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                bedtimeRequest
            )
        } catch (t: Throwable) {
            Timber.e(t, "Failed to schedule watchdog")
        }
    }

    companion object {
        const val CHANNEL_GUARDIAN = "guardian_channel"
        const val WATCHDOG_WORK_NAME = "guardian_watchdog"
        const val DNS_WORK_NAME = "guardian_dns_schedule"
        const val BEDTIME_WORK_NAME = "guardian_bedtime"
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