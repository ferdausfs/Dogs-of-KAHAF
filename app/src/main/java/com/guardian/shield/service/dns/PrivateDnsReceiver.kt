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
        runCatching {
            val c = PrivateDnsScheduler.readCache(context)
            val inWindow = PrivateDnsScheduler.isInWindow(
                PrivateDnsScheduler.nowMinutes(), c.startMin, c.endMin
            )
            val outcome = PrivateDnsController.applyDesiredState(
                context, c.enabled, inWindow, c.host, PrivateDnsScheduler.cache(context)
            )
            Timber.i("PrivateDns: tick — inWindow=$inWindow outcome=$outcome")
        }.onFailure { Timber.e(it, "PrivateDns: tick failed") }
        PrivateDnsScheduler.reschedule(context)
    }
}
