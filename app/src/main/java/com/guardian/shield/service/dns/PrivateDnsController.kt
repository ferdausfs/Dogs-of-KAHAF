package com.guardian.shield.service.dns

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import timber.log.Timber

/**
 * Applies the Private DNS desired state (R5 — DNS Auto Mode).
 *
 * HARD TRUTH (told to the owner too): a plain Device Admin / normal app can
 * NOT touch Private DNS — Google keeps `Settings.Global` behind
 * `WRITE_SECURE_SETTINGS`, which parents grant ONCE via ADB:
 *
 *   adb shell pm grant com.guardian.shield android.permission.WRITE_SECURE_SETTINGS
 *
 * So every write goes through [hasPermission] + runCatching: if the grant is
 * missing the feature simply no-ops and the settings screen shows the guided
 * banner. Nothing crashes, ever.
 *
 * State machine (cache lives in [PrivateDnsScheduler]'s SharedPreferences):
 *  - Transition OFF→ON: we first CAPTURE the user's own mode/specifier
 *    (`prev_mode`/`prev_spec`) and mark `auto_applied=true`, then write
 *    hostname mode.
 *  - Transition ON→OFF: only if `auto_applied` do we RESTORE the captured
 *    values (hostname+specifier if the user had their own DoT, else
 *    off/opportunistic) — we never stomp a setting we didn't make.
 *  - Manual "turn on now" test buttons bypass `auto_applied`, so the engine
 *    never "helpfully" resets a DNS the user set by hand.
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

    private const val C_PREV_MODE = "prev_mode"           // captured user mode
    private const val C_PREV_SPEC = "prev_spec"           // captured user specifier
    private const val C_AUTO_APPLIED = "auto_applied"     // we own the current writing

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Current global Private DNS mode ("off"/"opportunistic"/"hostname"/null). */
    fun currentMode(context: Context): String? = runCatching {
        Settings.Global.getString(context.contentResolver, KEY_MODE)
    }.getOrNull()

    fun currentSpecifier(context: Context): String? = runCatching {
        Settings.Global.getString(context.contentResolver, KEY_SPECIFIER)
    }.getOrNull()

    /**
     * Enforce the desired state for RIGHT NOW given the schedule.
     * @return a short human-readable outcome for logs/status, or null when the
     *         feature is inert (disabled / no host / no permission).
     */
    fun applyDesiredState(
        context: Context,
        enabled: Boolean,
        inWindow: Boolean,
        host: String,
        prefs: SharedPreferences
    ): String? {
        if (!enabled || host.isBlank()) return null
        if (!hasPermission(context)) {
            Timber.w("PrivateDns: WRITE_SECURE_SETTINGS not granted — skipping")
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
        hasPermission(context) && host.isNotBlank() && writeHostname(context, host)

    /** Manual test: put Private DNS back to automatic/off right now. */
    fun forceRestore(context: Context, prefs: SharedPreferences): Boolean {
        if (!hasPermission(context)) return false
        val prevMode = prefs.getString(C_PREV_MODE, MODE_OFF) ?: MODE_OFF
        val prevSpec = prefs.getString(C_PREV_SPEC, "") ?: ""
        val ok = restore(context, prevMode, prevSpec)
        if (ok) prefs.edit().putBoolean(C_AUTO_APPLIED, false).apply()
        return ok
    }

    private fun writeHostname(context: Context, host: String): Boolean = runCatching {
        val cr = context.contentResolver
        Settings.Global.putString(cr, KEY_SPECIFIER, host) &&
            Settings.Global.putString(cr, KEY_MODE, MODE_HOSTNAME)
    }.onFailure { Timber.e(it, "PrivateDns: set hostname failed") }.getOrDefault(false)

    private fun restore(context: Context, mode: String, spec: String): Boolean = runCatching {
        val cr = context.contentResolver
        if (mode == MODE_HOSTNAME && spec.isNotBlank()) {
            Settings.Global.putString(cr, KEY_SPECIFIER, spec)
        }
        Settings.Global.putString(cr, KEY_MODE,
            if (mode == MODE_HOSTNAME && spec.isBlank()) MODE_OFF else mode)
    }.onFailure { Timber.e(it, "PrivateDns: restore failed") }.getOrDefault(false)
}
