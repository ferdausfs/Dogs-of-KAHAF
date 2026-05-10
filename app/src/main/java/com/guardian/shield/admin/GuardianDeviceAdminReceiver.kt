package com.guardian.shield.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.guardian.shield.service.blocker.GuardianForegroundService
import timber.log.Timber

/**
 * Device Admin entry-point.
 *
 *  WHY:
 *    - Once the user grants Device-Admin to Guardian Shield, the app
 *      cannot be uninstalled normally — they have to disable admin first.
 *      This stops the most common bypass: "uninstall in 3 taps".
 *    - Some OEMs (MIUI / ColorOS / FunTouch / Realme UI) treat device-admin
 *      apps as "protected" and stop killing them aggressively in the
 *      background, which directly fixes the reported issue
 *      "sob thik ase kintu app kaj kore na" (everything looks ok but app
 *      is not working) — the OS had silently killed the service.
 *
 *  IMPORTANT:
 *    - Device Admin is OPTIONAL. The rest of the app works without it.
 *    - We deliberately do NOT request DevicePolicyManager.LOCK_TASK or
 *      password policies — those are intrusive. We only need the
 *      "uninstall + force-stop" protection.
 */
class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Timber.i("DeviceAdmin ENABLED — uninstall protection active")
        Toast.makeText(
            context,
            "Guardian Shield admin enabled — app is now uninstall-protected",
            Toast.LENGTH_LONG
        ).show()
        // Restart foreground service immediately so protection is active.
        runCatching { GuardianForegroundService.start(context) }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling Guardian Shield admin will let anyone uninstall " +
                "the blocker. Are you sure?"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Timber.w("DeviceAdmin DISABLED")
    }

    companion object {
        fun componentName(ctx: Context): ComponentName =
            ComponentName(ctx, GuardianDeviceAdminReceiver::class.java)
    }
}
