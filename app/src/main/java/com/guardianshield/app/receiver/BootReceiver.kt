package com.guardianshield.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.guardianshield.app.service.ProtectionForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a == Intent.ACTION_BOOT_COMPLETED ||
            a == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            a == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ContextCompat.startForegroundService(
                context, Intent(context, ProtectionForegroundService::class.java)
            )
        }
    }
}
