package com.guardian.shield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardian.shield.service.blocker.GuardianForegroundService
import timber.log.Timber

/**
 * v11 (2.1.1) STABILITY PATCH:
 *  • DEFENSIVE: onReceive() body fully wrapped in runCatching — a
 *    BroadcastReceiver that throws can ANR the entire process (since
 *    Android 13). This was an additional crash entry-point.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RESTART_SERVICE = "com.guardian.shield.action.RESTART_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        runCatching {
            val action = intent?.action ?: return

            val isOwnPackageReplaced = action == Intent.ACTION_PACKAGE_REPLACED &&
                intent.data?.schemeSpecificPart == context.packageName

            val accepted = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == ACTION_RESTART_SERVICE ||
                isOwnPackageReplaced

            if (!accepted) return

            runCatching {
                GuardianForegroundService.start(context)
            }.onFailure { Timber.w(it, "Failed to start service for action=$action") }
        }.onFailure { Timber.e(it, "BootReceiver onReceive crashed — suppressed") }
    }
}
