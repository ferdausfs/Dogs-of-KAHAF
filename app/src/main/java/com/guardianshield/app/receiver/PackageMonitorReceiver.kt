package com.guardianshield.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardianshield.app.ui.admin.TamperAlertActivity

/** Fires if our package somehow got removed — sends a parent alert via SMS/notification. */
class PackageMonitorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_REMOVED ||
            intent.action == Intent.ACTION_PACKAGE_FULLY_REMOVED) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            if (pkg == context.packageName) {
                context.startActivity(Intent(context, TamperAlertActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("reason", "package_removed"))
            }
        }
    }
}
