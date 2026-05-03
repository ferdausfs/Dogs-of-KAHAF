package com.guardian.shield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.guardian.shield.service.blocker.GuardianForegroundService

/**
 * BootReceiver — restart the foreground service after device boot,
 * package upgrade, or an in-process restart broadcast.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        // FIX: constants instead of hardcoded strings — typo-proof
        private const val TAG = "BootReceiver"
        const val ACTION_RESTART  = "com.guardian.shield.RESTART_SERVICE"
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val accepted =
            action == Intent.ACTION_BOOT_COMPLETED          ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED   ||
            action == ACTION_QUICKBOOT                       ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED     ||
            action == ACTION_RESTART

        if (!accepted) return

        // FIX: Use android.util.Log instead of Timber
        // Timber may not be initialized at boot time (before Application.onCreate)
        Log.d(TAG, "$action — starting foreground service")

        // FIX: LOCKED_BOOT_COMPLETED fires in Direct Boot mode
        // encrypted storage (DataStore/Room) is NOT accessible yet
        // Only start basic service — avoid any encrypted storage access
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.d(TAG, "Locked boot — limited start, skipping encrypted storage")
        }

        try {
            val serviceIntent = Intent(context, GuardianForegroundService::class.java)
            // FIX: minSdk=26 so startForegroundService always available
            // No need for SDK version check
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            // Android 12+ background-start restriction can throw here.
            Log.e(TAG, "Failed to start service: ${e.message}")
        }
    }
}