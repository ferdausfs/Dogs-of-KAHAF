package com.guardian.shield.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.guardian.shield.admin.GuardianDeviceAdminReceiver

/**
 * Centralised permission status + intents.
 *
 *  WHY this file exists:
 *    The user reported "permission auto remove hoy" — Android 11+ silently
 *    revokes permissions for "unused" apps (App Hibernation), and on many
 *    OEMs the Accessibility service gets disabled by Battery Saver. We need
 *    ONE place that knows the status of every permission the app cares
 *    about, so the UI can re-prompt the user the moment something is off.
 *
 *  This file does NOT change any existing logic — it is read-only checks +
 *  intent factories. Existing flows keep working unchanged.
 */
object PermissionManager {

    enum class PermissionKey {
        ACCESSIBILITY,
        OVERLAY,
        USAGE_STATS,
        BATTERY_UNRESTRICTED,
        NOTIFICATIONS,
        AUTO_REVOKE_DISABLED,
        DEVICE_ADMIN
    }

    data class Status(val key: PermissionKey, val granted: Boolean, val critical: Boolean)

    fun snapshot(ctx: Context): List<Status> = listOf(
        Status(PermissionKey.ACCESSIBILITY,        isAccessibilityEnabled(ctx),        critical = true),
        Status(PermissionKey.OVERLAY,              canDrawOverlays(ctx),               critical = true),
        Status(PermissionKey.USAGE_STATS,          hasUsageStats(ctx),                 critical = false),
        Status(PermissionKey.BATTERY_UNRESTRICTED, isIgnoringBatteryOptimisations(ctx),critical = true),
        Status(PermissionKey.NOTIFICATIONS,        notificationsEnabled(ctx),          critical = true),
        Status(PermissionKey.AUTO_REVOKE_DISABLED, isAutoRevokeDisabled(ctx),          critical = true),
        Status(PermissionKey.DEVICE_ADMIN,         isDeviceAdminActive(ctx),           critical = false)
    )

    fun missingCritical(ctx: Context): List<PermissionKey> =
        snapshot(ctx).filter { it.critical && !it.granted }.map { it.key }

    // ----------------------------- checks --------------------------------

    fun isAccessibilityEnabled(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.id.contains(ctx.packageName) }
    }

    fun canDrawOverlays(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(ctx) else true

    fun hasUsageStats(ctx: Context): Boolean = runCatching {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), ctx.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), ctx.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun isIgnoringBatteryOptimisations(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun notificationsEnabled(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /**
     * On Android 11+ (API 30), the OS auto-revokes runtime permissions for
     * "unused" apps. `PackageManager.isAutoRevokeWhitelisted == true` means
     * the user has whitelisted us → auto-revoke is OFF → which is what we
     * want. So we return `isAutoRevokeWhitelisted` as the "granted" state.
     * Pre-API-30 there is no auto-revoke, so we return true.
     */
    fun isAutoRevokeDisabled(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return runCatching { ctx.packageManager.isAutoRevokeWhitelisted }
            .getOrDefault(false)
    }

    fun isDeviceAdminActive(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(GuardianDeviceAdminReceiver.componentName(ctx))
    }

    // ----------------------------- intents -------------------------------

    fun intentFor(ctx: Context, key: PermissionKey): Intent? = when (key) {
        PermissionKey.ACCESSIBILITY ->
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        PermissionKey.OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${ctx.packageName}")
        )

        PermissionKey.USAGE_STATS ->
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

        PermissionKey.BATTERY_UNRESTRICTED ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${ctx.packageName}"))
            else null

        PermissionKey.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            else null

        PermissionKey.AUTO_REVOKE_DISABLED ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS,
                    Uri.parse("package:${ctx.packageName}"))
            else null

        PermissionKey.DEVICE_ADMIN -> {
            val cn: ComponentName = GuardianDeviceAdminReceiver.componentName(ctx)
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Activate to prevent casual uninstall and stop the OS from killing Guardian Shield in the background."
                )
        }
    }

    fun label(key: PermissionKey): String = when (key) {
        PermissionKey.ACCESSIBILITY        -> "Accessibility Service"
        PermissionKey.OVERLAY              -> "Display over other apps"
        PermissionKey.USAGE_STATS          -> "Usage access"
        PermissionKey.BATTERY_UNRESTRICTED -> "Unrestricted battery"
        PermissionKey.NOTIFICATIONS        -> "Notifications"
        PermissionKey.AUTO_REVOKE_DISABLED -> "Disable permission auto-reset"
        PermissionKey.DEVICE_ADMIN         -> "Device admin (uninstall protection)"
    }

    fun description(key: PermissionKey): String = when (key) {
        PermissionKey.ACCESSIBILITY ->
            "Required. Lets Guardian read screen content and block apps."
        PermissionKey.OVERLAY ->
            "Required. Used to draw the full-screen block overlay."
        PermissionKey.USAGE_STATS ->
            "Optional but recommended. Improves foreground-app detection on some OEMs."
        PermissionKey.BATTERY_UNRESTRICTED ->
            "Required. Stops Android / OEM battery savers from killing the protection service."
        PermissionKey.NOTIFICATIONS ->
            "Required. The persistent notification keeps the foreground service alive."
        PermissionKey.AUTO_REVOKE_DISABLED ->
            "Required on Android 11+. Without this, Android automatically revokes permissions if you don't open the app for a few months."
        PermissionKey.DEVICE_ADMIN ->
            "Recommended. Prevents casual uninstall and helps the app survive aggressive OEM background killers (MIUI, ColorOS, FunTouch, Realme)."
    }
}
