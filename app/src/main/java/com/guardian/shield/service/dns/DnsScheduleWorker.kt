package com.guardian.shield.service.dns

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
 * R5 — periodic self-healer for DNS Auto Mode (every 15 min, WorkManager):
 *  1. mirrors DataStore schedule -> sync cache (UI truth -> engine truth),
 *  2. enforces desired DNS state for the current time (covers missed alarms,
 *     reboots, timezone hops),
 *  3. re-arms the exact next boundary alarm.
 */
@HiltWorker
class DnsScheduleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val prefs: GuardianPreferences
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val enabled = prefs.dnsAutoEnabled.first()
            val startMin = prefs.dnsAutoStartMin.first()
            val endMin = prefs.dnsAutoEndMin.first()
            val host = prefs.dnsAutoHost.first()
            // R8 — day mask + pause ride along so the alarm receiver and QS
            // tile can read them synchronously from the plain-prefs cache.
            val dayMask = prefs.dnsAutoDayMask.first()
            val pauseUntilMs = prefs.dnsAutoPauseUntilMs.first()

            PrivateDnsScheduler.syncCache(
                applicationContext, enabled, startMin, endMin, host, dayMask, pauseUntilMs
            )
            val effective = PrivateDnsScheduler.isEffectiveNow(
                PrivateDnsScheduler.nowMinutes(), startMin, endMin, dayMask, pauseUntilMs
            )
            val outcome = PrivateDnsController.applyDesiredState(
                applicationContext, enabled, effective, host, PrivateDnsScheduler.cache(applicationContext)
            )
            PrivateDnsScheduler.reschedule(applicationContext)
            if (outcome != null) Timber.d("PrivateDns worker: $outcome")
            Result.success()
        } catch (t: Throwable) {
            Timber.e(t, "DnsScheduleWorker failed")
            Result.retry()
        }
    }
}
