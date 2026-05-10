package com.guardian.shield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardian.shield.service.blocker.GuardianForegroundService
import timber.log.Timber

/**
 * v8 FIX-LOG (stability pass):
 *  • BUG-06 → handles a new ACTION_RESTART_SERVICE broadcast scheduled by
 *    GuardianForegroundService.onTaskRemoved via AlarmManager. This avoids
 *    the Android 12+ ForegroundServiceDidNotStartInTimeException race.
 *  • BUG-07 → PACKAGE_REPLACED check now uses exact-match on the
 *    schemeSpecificPart instead of String.contains(), which would have
 *    matched any package whose name shares our prefix.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        /** Custom action used by FGS self-restart alarm (BUG-06). */
        const val ACTION_RESTART_SERVICE = "com.guardian.shield.action.RESTART_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        // BUG-07: exact match on the package URI's schemeSpecificPart.
        val isOwnPackageReplaced = action == Intent.ACTION_PACKAGE_REPLACED &&
            intent.data?.schemeSpecificPart == context.packageName

        val accepted = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == ACTION_RESTART_SERVICE ||  // BUG-06
            isOwnPackageReplaced

        if (!accepted) return

        runCatching {
            GuardianForegroundService.start(context)
        }.onFailure { Timber.w(it, "Failed to start service for action=$action") }
    }
}
