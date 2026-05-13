package com.guardian.shield.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.guardian.shield.service.blocker.GuardianForegroundService
import com.guardian.shield.service.detection.TimeLockManager
import timber.log.Timber

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Timber.i("Device Admin enabled")
        runCatching { GuardianForegroundService.start(context) }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Timber.w("Device Admin disabled!")

        // Commitment Lock active থাকলে তাৎক্ষণিক re-request করো
        val tlm = TimeLockManager(context)
        if (tlm.isLocked() || tlm.isInCooldown()) {
            Timber.w("Lock active — re-requesting Device Admin immediately")
            runCatching {
                val admin = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
                val reEnable = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "🔒 Commitment Lock সক্রিয় — সুরক্ষা বজায় রাখতে Device Admin আবার চালু করুন।"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(reEnable)
            }
        }
        // Service চালু রাখো
        runCatching { GuardianForegroundService.start(context) }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val tlm = TimeLockManager(context)
        return when {
            tlm.isInCooldown() ->
                "🔒 Unlock cooldown চলছে — ${tlm.getRemainingFormatted()}। " +
                "Device Admin বন্ধ করলেও lock শেষ না হওয়া পর্যন্ত আবার চালু করতে বলা হবে।"
            tlm.isLocked() ->
                "🔒 Commitment Lock সক্রিয়! Unlock request না দেওয়া পর্যন্ত " +
                "Device Admin বন্ধ করলেও আবার চালু করতে বলা হবে।"
            else ->
                "⚠️ Guardian Shield বন্ধ করলে সমস্ত সুরক্ষা বন্ধ হয়ে যাবে।"
        }
    }
}
