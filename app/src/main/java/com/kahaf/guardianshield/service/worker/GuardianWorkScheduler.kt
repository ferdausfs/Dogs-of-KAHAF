package com.kahaf.guardianshield.service.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kahaf.guardianshield.service.receiver.MinuteTickReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the periodic background work + the AlarmManager minute ticker.
 *
 *  - PeriodicWork "schedule_recompute": Doze-friendly, runs every 15 min minimum
 *    (the WorkManager floor) and recomputes timed blocks.
 *  - PeriodicWork "lock_pruner": cleans expired AppLock rows once an hour.
 *  - MinuteTickReceiver covers the tighter ≤60s response on schedule boundaries.
 */
@Singleton
class GuardianWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scheduleAll() {
        scheduleScheduleRecompute()
        scheduleLockPruner()
        MinuteTickReceiver.schedule(context)
    }

    private fun scheduleScheduleRecompute() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<ScheduleRecomputeWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(TAG_SCHEDULE)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_SCHEDULE_RECOMPUTE,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun scheduleLockPruner() {
        val request = PeriodicWorkRequestBuilder<LockPrunerWorker>(
            1, TimeUnit.HOURS
        )
            .addTag(TAG_LOCK)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_LOCK_PRUNER,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_SCHEDULE_RECOMPUTE)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_LOCK_PRUNER)
    }

    companion object {
        const val WORK_SCHEDULE_RECOMPUTE = "guardian_schedule_recompute"
        const val WORK_LOCK_PRUNER = "guardian_lock_pruner"
        const val TAG_SCHEDULE = "tag_schedule"
        const val TAG_LOCK = "tag_lock"
    }
}
