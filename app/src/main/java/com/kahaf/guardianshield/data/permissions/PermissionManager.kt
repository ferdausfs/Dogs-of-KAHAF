package com.kahaf.guardianshield.data.permissions

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import com.kahaf.guardianshield.admin.GuardianDeviceAdminReceiver
import com.kahaf.guardianshield.service.accessibility.GuardianAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshots permission grant status (cached 10s). Compose-friendly via [refresh] +
 * `current()` getters. Provides deep-links to each system settings screen.
 *
 * v3.1.1 FIX: every system-Settings deep-link is now wrapped in
 * runCatching — some OEM ROMs (notably stripped-down MIUI / older Huawei
 * EMUI) don't ship the activity that handles
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` /
 * `ACTION_MANAGE_OVERLAY_PERMISSION`, and an unhandled
 * ActivityNotFoundException here used to crash the onboarding flow.
 *
 * v3.0.0 + legacy merge:
 *   - added Device Admin (uninstall protection) tracking
 *   - added auto-revoke / app-hibernation tracking (Android 11+)
 *   - added intent helpers for all of the above
 */
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Snapshot(
        val accessibility: Boolean,
        val overlay: Boolean,
        val notifications: Boolean,
        val ignoreBatteryOpt: Boolean,
        val deviceAdmin: Boolean,
        val autoRevokeDisabled: Boolean,
        val capturedAtMs: Long
    ) {
        val allCriticalGranted: Boolean
            get() = accessibility && overlay && notifications
    }

    @Volatile private var cached: Snapshot? = null

    fun current(): Snapshot {
        val c = cached
        val now = System.currentTimeMillis()
        if (c != null && (now - c.capturedAtMs) < CACHE_MS) return c
        return refresh()
    }

    fun refresh(): Snapshot {
        val snap = Snapshot(
            accessibility = isAccessibilityEnabled(),
            overlay = canDrawOverlays(),
            notifications = isNotificationsAllowed(),
            ignoreBatteryOpt = isIgnoringBatteryOptimizations(),
            deviceAdmin = isDeviceAdminActive(),
            autoRevokeDisabled = isAutoRevokeDisabled(),
            capturedAtMs = System.currentTimeMillis()
        )
        cached = snap
        return snap
    }

    fun invalidateCache() {
        cached = null
    }

    fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(context, GuardianAccessibilityService::class.java)
            .flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        for (s in splitter) {
            if (s.equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun isNotificationsAllowed(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    fun isDeviceAdminActive(): Boolean = runCatching {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.isAdminActive(GuardianDeviceAdminReceiver.componentName(context))
    }.getOrDefault(false)

    /**
     * On Android 11+ (API 30), the OS auto-revokes runtime permissions for
     * "unused" apps. `isAutoRevokeWhitelisted == true` means the user has
     * whitelisted us → auto-revoke is OFF. Pre-API-30 there is no auto-revoke.
     */
    fun isAutoRevokeDisabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return runCatching { context.packageManager.isAutoRevokeWhitelisted }
            .getOrDefault(false)
    }

    // ───── deep-links (v3.1.1: all wrapped in runCatching) ─────
    fun openAccessibilitySettings() = startSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun openOverlaySettings() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.w(TAG, "openOverlaySettings failed", it) }
    }

    fun openNotificationSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { Log.w(TAG, "openNotificationSettings failed", it) }
    }

    fun openBatteryOptimizationSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            Log.w(TAG, "openBatteryOptimizationSettings failed, falling back", it)
            // Fallback to the generic battery-optimization screen so the user
            // can at least find the setting manually on stripped OEM ROMs.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /** Launch the system "Activate Device Admin?" prompt. */
    fun requestDeviceAdmin() {
        val cn: ComponentName = GuardianDeviceAdminReceiver.componentName(context)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Activate to prevent casual uninstall and stop the OS from killing Guardian Shield in the background."
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "requestDeviceAdmin failed", it) }
    }

    /** Programmatically deactivate Device Admin (the only safe way). */
    fun removeDeviceAdmin() {
        runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.removeActiveAdmin(GuardianDeviceAdminReceiver.componentName(context))
        }.onFailure { Log.w(TAG, "removeDeviceAdmin failed", it) }
    }

    /** Send the user to the auto-revoke / hibernation exclusion screen. */
    fun requestDisableAutoRevoke() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val intent = Intent(
            Intent.ACTION_AUTO_REVOKE_PERMISSIONS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "requestDisableAutoRevoke failed", it) }
    }

    private fun startSettings(action: String) {
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "startSettings($action) failed", it) }
    }

    companion object {
        private const val TAG = "PermissionManager"
        private const val CACHE_MS = 10_000L
    }
}
