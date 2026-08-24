package com.guardian.shield.service.dns

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import com.guardian.shield.service.dns.shizuku.ShizukuDns
import timber.log.Timber

/**
 * Applies the Private DNS desired state — R7: FOUR engines, picked at call
 * time by what the device actually offers, NO computer required anywhere:
 *
 *  1. PERMANENT   — the app holds WRITE_SECURE_SETTINGS (one-time grant;
 *                   either classic ADB, or granted BY ITSELF via Shizuku's
 *                   one-tap `pm grant` — permanent, survives reboots).
 *  2. DEVICE_OWNER— app is provisioned device/profile owner: Android's own
 *                   DevicePolicyManager sets Private DNS with ZERO setup.
 *  3. ROOT        — rooted phone: `su -c settings put …` needs nothing else.
 *  4. SHIZUKU     — interim fallback shell until the permanent grant exists.
 *
 * If no engine is available the feature no-ops and the settings screen shows
 * the guided banner (Shizuku/owner first, ADB as last resort). Never crashes;
 * every engine is capability-probed before use.
 *
 * State machine (cache lives in [PrivateDnsScheduler]'s SharedPreferences):
 *  - OFF→ON: CAPTURE the user's own mode/specifier, mark auto_applied, write hostname.
 *  - ON→OFF: only if auto_applied, RESTORE the captured values — never stomp
 *    a DNS setting we didn't make. Manual test buttons bypass the state machine.
 */
object PrivateDnsController {

    const val PERMISSION = Manifest.permission.WRITE_SECURE_SETTINGS
    const val ADB_COMMAND =
        "adb shell pm grant com.guardian.shield android.permission.WRITE_SECURE_SETTINGS"

    private const val KEY_MODE = "private_dns_mode"
    private const val KEY_SPECIFIER = "private_dns_specifier"

    const val MODE_OFF = "off"
    const val MODE_OPPORTUNISTIC = "opportunistic"
    const val MODE_HOSTNAME = "hostname"

    private const val C_PREV_MODE = "prev_mode"
    private const val C_PREV_SPEC = "prev_spec"
    private const val C_AUTO_APPLIED = "auto_applied"

    /** Which control path is usable RIGHT NOW, best first. */
    enum class Engine { PERMANENT, DEVICE_OWNER, ROOT, SHIZUKU, NONE }

    /**
     * Pick the best live engine. May briefly exec `su` on first call (cached
     * afterwards) — call off the main thread.
     */
    fun activeEngine(context: Context): Engine = when {
        hasPermission(context) -> Engine.PERMANENT
        DnsDeviceOwner.isOwner(context) -> Engine.DEVICE_OWNER
        DnsRoot.hasRoot() -> Engine.ROOT
        ShizukuDns.hasShizukuPermission() -> Engine.SHIZUKU
        else -> Engine.NONE
    }

    /** Any working DNS-control path at all. */
    fun hasControl(context: Context): Boolean = activeEngine(context) != Engine.NONE

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------- reads

    /**
     * Read a Settings.Global value. Plain reads need NO permission (Android
     * only restricts WRITES), so this works on every device — engine paths
     * are kept as belt-and-braces fallbacks for locked-down ROMs.
     */
    private fun readGlobal(context: Context, key: String): String? {
        runCatching { Settings.Global.getString(context.contentResolver, key) }
            .getOrNull()
            ?.takeUnless { it.isEmpty() || it == "null" }
            ?.let { return it }
        if (DnsRoot.hasRoot()) return DnsRoot.getGlobal(key)
        if (ShizukuDns.hasShizukuPermission() && ShizukuDns.ensureBound()) {
            val out = ShizukuDns.run("settings get global $key") ?: return null
            val line = out.lineSequence().drop(1).firstOrNull()?.trim()
            return line?.takeUnless { it.isEmpty() || it == "null" }
        }
        return null
    }

    /** Current global Private DNS mode ("off"/"opportunistic"/"hostname"/null). */
    fun currentMode(context: Context): String? = readGlobal(context, KEY_MODE)

    fun currentSpecifier(context: Context): String? = readGlobal(context, KEY_SPECIFIER)

    // ------------------------------------------------------------- writes

    private fun writeGlobal(context: Context, key: String, value: String): Boolean {
        if (hasPermission(context)) {
            return runCatching {
                Settings.Global.putString(context.contentResolver, key, value)
            }.onFailure { Timber.e(it, "settings put failed") }.getOrDefault(false)
        }
        if (DnsRoot.hasRoot() && DnsRoot.putGlobal(key, value)) return true
        if (ShizukuDns.hasShizukuPermission() && ShizukuDns.ensureBound()) {
            val out = ShizukuDns.run("settings put global $key $value")
            return out?.startsWith("0\n") == true
        }
        return false
    }

    /** Turn filtered DNS ON via the best live engine. */
    private fun writeHostname(context: Context, host: String): Boolean =
        when (activeEngine(context)) {
            Engine.DEVICE_OWNER -> DnsDeviceOwner.setHost(context, host)
            Engine.NONE -> false
            else -> writeGlobal(context, KEY_SPECIFIER, host) &&
                writeGlobal(context, KEY_MODE, MODE_HOSTNAME)
        }

    /** Put the user's own setting back via the best live engine. */
    private fun restore(context: Context, mode: String, spec: String): Boolean {
        if (activeEngine(context) == Engine.DEVICE_OWNER) {
            // DPM can only express "always-on host" or "opportunistic":
            // hostname+spec restores exactly, anything else maps to
            // opportunistic ("Automatic") — the closest neutral state.
            return if (mode == MODE_HOSTNAME && spec.isNotBlank()) {
                DnsDeviceOwner.setHost(context, spec)
            } else {
                if (mode == MODE_OFF) Timber.i("DnsDeviceOwner: MODE_OFF restores as opportunistic")
                DnsDeviceOwner.setOpportunistic(context)
            }
        }
        if (mode == MODE_HOSTNAME && spec.isNotBlank()) {
            writeGlobal(context, KEY_SPECIFIER, spec)
        }
        return writeGlobal(
            context, KEY_MODE,
            if (mode == MODE_HOSTNAME && spec.isBlank()) MODE_OFF else mode
        )
    }

    // ------------------------------------------------------------- policy

    /**
     * Enforce the desired state for RIGHT NOW given the schedule.
     * @return a short human-readable outcome for logs/status, or null when the
     *         feature is inert (disabled / no host / no control path).
     */
    fun applyDesiredState(
        context: Context,
        enabled: Boolean,
        inWindow: Boolean,
        host: String,
        prefs: SharedPreferences
    ): String? {
        if (!enabled || host.isBlank()) return null
        if (!hasControl(context)) {
            Timber.w("PrivateDns: no DNS control (grant/owner/root/shizuku missing)")
            return null
        }

        val autoApplied = prefs.getBoolean(C_AUTO_APPLIED, false)
        val curMode = currentMode(context)
        val curSpec = currentSpecifier(context)

        return if (inWindow) {
            if (curMode == MODE_HOSTNAME && curSpec.equals(host, ignoreCase = true)) {
                "dns-on (already)"
            } else if (writeHostname(context, host)) {
                if (!autoApplied) {
                    prefs.edit()
                        .putString(C_PREV_MODE, curMode ?: MODE_OFF)
                        .putString(C_PREV_SPEC, curSpec ?: "")
                        .putBoolean(C_AUTO_APPLIED, true)
                        .apply()
                }
                Timber.i("PrivateDns: ON -> $host")
                "dns-on ($host)"
            } else null
        } else {
            if (autoApplied) {
                val prevMode = prefs.getString(C_PREV_MODE, MODE_OFF) ?: MODE_OFF
                val prevSpec = prefs.getString(C_PREV_SPEC, "") ?: ""
                if (restore(context, prevMode, prevSpec)) {
                    prefs.edit().putBoolean(C_AUTO_APPLIED, false).apply()
                    Timber.i("PrivateDns: OFF -> restored $prevMode")
                    "dns-off (restored $prevMode)"
                } else null
            } else "dns-off (user-managed)"
        }
    }

    /** Manual test: force the filtered hostname ON right now (no state capture). */
    fun forceOn(context: Context, host: String): Boolean =
        host.isNotBlank() && writeHostname(context, host)

    /** Manual test: put Private DNS back to the saved/previous state. */
    fun forceRestore(context: Context, prefs: SharedPreferences): Boolean {
        val prevMode = prefs.getString(C_PREV_MODE, MODE_OFF) ?: MODE_OFF
        val prevSpec = prefs.getString(C_PREV_SPEC, "") ?: ""
        val ok = restore(context, prevMode, prevSpec)
        if (ok) prefs.edit().putBoolean(C_AUTO_APPLIED, false).apply()
        return ok
    }
}
