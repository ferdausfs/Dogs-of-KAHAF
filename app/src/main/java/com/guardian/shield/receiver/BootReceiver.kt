package com.guardian.shield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardian.shield.service.blocker.GuardianForegroundService
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Timber.i("BootReceiver received: %s", action)
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                runCatching { GuardianForegroundService.start(context) }
            }
        }
    }
}
