package com.guardianshield.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.guardianshield.app.manager.PinManager
import com.guardianshield.app.ui.admin.PinActivity
import com.guardianshield.app.ui.admin.TamperAlertActivity

/** Asks for parent PIN before Device Admin can be disabled. */
class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Trigger PIN gate immediately.
        if (PinManager.hasPin(context)) {
            context.startActivity(Intent(context, PinActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("mode", "verify_admin_disable"))
        }
        return "⚠️ Parental Control is active. Disabling requires the parent PIN."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // Last-ditch alarm.
        context.startActivity(Intent(context, TamperAlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("reason", "device_admin_disabled"))
    }
}
