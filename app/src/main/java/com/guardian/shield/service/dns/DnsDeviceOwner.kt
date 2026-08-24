package com.guardian.shield.service.dns

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.guardian.shield.admin.GuardianDeviceAdminReceiver
import timber.log.Timber

/**
 * R7 — DEVICE OWNER / PROFILE OWNER engine for Private DNS.
 *
 * If Guardian Shield is provisioned as the device owner (or the profile
 * owner of a managed profile), Android's own [DevicePolicyManager] API can
 * set Private DNS DIRECTLY — no permission grant, no ADB, no Shizuku, no
 * computer at all. This is the cleanest zero-setup path the platform offers.
 *
 * Everything is capability-probed at runtime, so devices where the app is a
 * mere device admin simply report "not available" and the controller falls
 * through to the next engine. Nothing here can crash the app.
 */
object DnsDeviceOwner {

    private fun dpm(context: Context): DevicePolicyManager? =
        runCatching {
            context.getSystemService(DevicePolicyManager::class.java)
        }.getOrNull()

    private fun admin(context: Context): ComponentName =
        ComponentName(context, GuardianDeviceAdminReceiver::class.java)

    /**
     * True when this app can call the Private DNS DPM APIs: it must be the
     * device owner (or a profile owner, e.g. COPE/work-profile setup) AND
     * the platform must expose those APIs (added in API 29).
     */
    fun isOwner(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val mgr = dpm(context) ?: return false
            mgr.isDeviceOwnerApp(context.packageName) ||
                mgr.isProfileOwnerApp(context.packageName)
        }.getOrDefault(false)
    }

    /** Set Private DNS to always-on with [host]. Returns true on success. */
    fun setHost(context: Context, host: String): Boolean {
        if (!isOwner(context) || host.isBlank()) return false
        return runCatching {
            val rc = dpm(context)!!
                .setGlobalPrivateDnsModeSpecifiedHost(admin(context), host)
            (rc == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR).also {
                if (!it) Timber.w("DnsDeviceOwner: setHost rc=$rc")
            }
        }.onFailure { Timber.e(it, "DnsDeviceOwner: setHost failed") }
            .getOrDefault(false)
    }

    /**
     * Set Private DNS back to "automatic" (opportunistic DoT). DPM exposes
     * no "off" setter, so this is as close to neutral as the API allows.
     */
    fun setOpportunistic(context: Context): Boolean {
        if (!isOwner(context)) return false
        return runCatching {
            val rc = dpm(context)!!
                .setGlobalPrivateDnsModeOpportunistic(admin(context))
            (rc == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR).also {
                if (!it) Timber.w("DnsDeviceOwner: setOpportunistic rc=$rc")
            }
        }.onFailure { Timber.e(it, "DnsDeviceOwner: setOpportunistic failed") }
            .getOrDefault(false)
    }
}
