package com.kahaf.guardian.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Skip LOCKED_BOOT_COMPLETED — EncryptedSharedPreferences unavailable before device unlock
        if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON"))
            KahafForegroundService.start(context)
    }
}
