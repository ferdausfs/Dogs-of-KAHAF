package com.guardian.shield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardian.shield.service.blocker.GuardianForegroundService
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Timber.i("BootReceiver: $action")
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                runCatching { GuardianForegroundService.start(context) }
                    .onFailure { Timber.e(it, "Failed to start on boot") }
                // R5 — re-arm the Private DNS window-boundary alarm after
                // reboot/update (the periodic worker re-syncs the cache and
                // enforces state within its first interval).
                runCatching { com.guardian.shield.service.dns.PrivateDnsScheduler.reschedule(context) }
                    .onFailure { Timber.e(it, "Failed to re-arm DNS schedule") }
            }
        }
    }
}