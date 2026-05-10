package com.kahaf.guardianshield.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.kahaf.guardianshield.service.foreground.GuardianForegroundService

/**
 * Device Admin entry-point — ported from the legacy v2.x codebase into the
 * v3.0.0 (kahaf) architecture.
 *
 *  WHY:
 *    - Once the user grants Device-Admin to Guardian Shield, the app
 *      cannot be uninstalled normally — they have to disable admin first.
 *      This stops the most common bypass: "uninstall in 3 taps".
 *    - Some OEMs (MIUI / ColorOS / FunTouch / Realme UI) treat device-admin
 *      apps as "protected" and stop killing them aggressively in the
 *      background, which directly fixes the historical "everything looks
 *      ok but the app stops working" issue — the OS had silently killed
 *      the foreground service.
 *
 *  IMPORTANT:
 *    - Device Admin is OPTIONAL. The rest of the app works without it.
 *    - We deliberately do NOT request password / lock-task / wipe policies
 *      — those are intrusive and Play-policy-risky. We only ask for
 *      `force-lock` so we satisfy the OS minimum.
 */
class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "DeviceAdmin ENABLED — uninstall protection active")
        runCatching {
            Toast.makeText(
                context,
                "Guardian Shield admin enabled — app is now uninstall-protected",
                Toast.LENGTH_LONG
            ).show()
        }
        // Restart foreground service immediately so protection is active.
        runCatching { GuardianForegroundService.start(context) }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling Guardian Shield admin will let anyone uninstall " +
                "the blocker. Are you sure?"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "DeviceAdmin DISABLED")
    }

    companion object {
        private const val TAG = "GuardianDeviceAdmin"
        fun componentName(ctx: Context): ComponentName =
            ComponentName(ctx, GuardianDeviceAdminReceiver::class.java)
    }
}
