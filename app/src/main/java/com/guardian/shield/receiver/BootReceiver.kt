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
 *
 *  v2 update:
 *   - Also restart the protection service on MY_PACKAGE_REPLACED /
 *     PACKAGE_REPLACED so an app update never leaves the user unprotected.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val accepted = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                (action == Intent.ACTION_PACKAGE_REPLACED &&
                        intent.dataString?.contains(context.packageName) == true)
        if (!accepted) return
        runCatching {
            GuardianForegroundService.start(context)
        }.onFailure { Timber.w(it, "Failed to start service for action=$action") }
    }
}
