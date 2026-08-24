package com.guardian.shield.service.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardian.shield.data.local.datastore.GuardianPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

/**
 * R7.5 — exact window-boundary tick for Bedtime Mode. goAsync + worker
 * thread: the tick touches DataStore (suspend), which must never run on the
 * main thread. Hilt injects [prefs] at the start of onReceive.
 */
@AndroidEntryPoint
class BedtimeReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: GuardianPreferences

    companion object {
        const val ACTION_TICK = "com.guardian.shield.action.BEDTIME_TICK"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TICK) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                runBlocking {
                    BedtimeScheduler.tick(appContext, prefs)
                }
                Timber.i("Bedtime: boundary tick applied")
            } catch (t: Throwable) {
                Timber.e(t, "Bedtime: tick failed")
            } finally {
                BedtimeScheduler.reschedule(appContext)
                pendingResult.finish()
            }
        }.start()
    }
}
