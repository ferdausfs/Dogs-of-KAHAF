package com.guardian.shield.service.dns

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Window-boundary tick for DNS Auto Mode (R5): fires exactly at the schedule
 * start/end, applies the desired state synchronously from the cache, then
 * arms the NEXT boundary. Cheap and sequential — no coroutines needed.
 */
class PrivateDnsReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TICK = "com.guardian.shield.action.PRIVATE_DNS_TICK"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TICK) return
        // R7.2 — engine paths now exec processes (Shizuku.newProcess / su),
        // which MUST NOT run on the main thread (v3.7.0's ensureBound did
        // exactly that and the tick silently no-op'd). goAsync + worker thread.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                val c = PrivateDnsScheduler.readCache(appContext)
                // R8 — effective state honours the day mask + 15-min pause.
                val effective = PrivateDnsScheduler.isEffectiveNow(
                    PrivateDnsScheduler.nowMinutes(), c.startMin, c.endMin,
                    c.dayMask, c.pauseUntilMs
                )
                val outcome = PrivateDnsController.applyDesiredState(
                    appContext, c.enabled, effective, c.host, PrivateDnsScheduler.cache(appContext)
                )
                Timber.i("PrivateDns: tick — effective=$effective outcome=$outcome")
            } catch (t: Throwable) {
                Timber.e(t, "PrivateDns: tick failed")
            } finally {
                PrivateDnsScheduler.reschedule(appContext)
                pendingResult.finish()
            }
        }.start()
    }
}
