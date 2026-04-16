package com.kahaf.guardian.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class KahafDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) { super.onEnabled(context, intent) }
    override fun onDisabled(context: Context, intent: Intent) { super.onDisabled(context, intent) }
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Disabling device admin will reduce protection. Are you sure?"
}
