package com.guardian.shield.admin

import android.app.admin.DeviceAdminReceiver
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
        runCatching { GuardianForegroundService.start(context) }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val timeLockManager = TimeLockManager(context)
        return if (timeLockManager.isLocked()) {
            "🔒 Commitment Lock সক্রিয়! ${timeLockManager.getRemainingFormatted()} পর্যন্ত Device Admin বন্ধ করা যাবে না।"
        } else {
            "⚠️ Guardian Shield বন্ধ করলে সমস্ত সুরক্ষা বন্ধ হয়ে যাবে। Harmful content আর block হবে না।"
        }
    }
}