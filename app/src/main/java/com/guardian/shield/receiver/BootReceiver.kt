package com.guardian.shield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardian.shield.service.blocker.GuardianForegroundService
import timber.log.Timber

/**
 * FIX-LOG (vs original):
 *  - BUG #13: API 31+ throws BackgroundServiceStartNotAllowedException if a
 *    receiver tries to start a foreground service for a "specialUse" foreground
 *    type from the background. BOOT_COMPLETED is one of the few exempted
 *    triggers, but the call must still be inside a try/catch because some OEMs
 *    are stricter than AOSP. We also accept LOCKED_BOOT_COMPLETED to start
 *    earlier on devices that use Direct Boot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        runCatching {
            GuardianForegroundService.start(context)
        }.onFailure { Timber.w(it, "Failed to start service on boot") }
    }
}
