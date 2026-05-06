package com.guardian.shield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Logs package install/uninstall events.
 * Future: could send notification "App X was uninstalled"
 */
class PackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        val action = intent.action ?: return
        Log.d("PackageReceiver", "$action: $pkg")
    }
}