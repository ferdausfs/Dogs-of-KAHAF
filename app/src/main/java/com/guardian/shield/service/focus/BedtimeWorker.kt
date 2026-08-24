package com.guardian.shield.service.focus

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.guardian.shield.data.local.datastore.GuardianPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * R7.5 — periodic self-healer for Bedtime Mode (every 15 min, WorkManager):
 * mirrors DataStore -> sync cache, enforces the desired focus state (covers
 * missed alarms, reboots, timezone hops), re-arms the exact boundary alarm.
 */
@HiltWorker
class BedtimeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val prefs: GuardianPreferences
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val enabled = prefs.bedtimeEnabled.first()
            val startMin = prefs.bedtimeStartMin.first()
            val endMin = prefs.bedtimeEndMin.first()

            BedtimeScheduler.syncCache(applicationContext, enabled, startMin, endMin)
            BedtimeScheduler.tick(applicationContext, prefs)
            BedtimeScheduler.reschedule(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Timber.e(t, "BedtimeWorker failed")
            Result.retry()
        }
    }
}
