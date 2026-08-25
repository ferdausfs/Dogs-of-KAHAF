package com.guardian.shield.service.detection

import android.content.Context
import android.provider.Settings
import timber.log.Timber

/**
 * R14 (v3.8.3) — app-data-wipe detection.
 *
 * "Clear data" destroys everything the app stores about itself — including
 * the evidence that a wipe happened. The ONE place on a stock device that
 * survives a data clear (and does not need an account or INTERNET) is
 * [Settings.Global] — writable only when the user has granted
 * WRITE_SECURE_SETTINGS (the same one-time grant Private DNS Auto Mode uses;
 * this feature adds NO new permission).
 *
 * Protocol (run once per app start, off the main thread):
 *  - local uuid present          → keep the sentinel fresh (covers a grant
 *                                  that arrived later).
 *  - local blank + sentinel set  → the data was WIPED: log a tamper event
 *                                  and adopt the surviving uuid (continuity).
 *  - both blank                  → genuine first install: mint persistently.
 * Without the secure grant the read returns null and the feature silently
 * degrades to "no detection" — never a false alarm.
 */
object TamperSentinel {

    private const val SECURE_KEY = "guardian_install_sentinel"

    /** Sentinel value, or null when unreadable / not set / grant missing. */
    fun read(context: Context): String? = runCatching {
        Settings.Global.getString(context.contentResolver, SECURE_KEY)
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** @return true when the value was persisted (i.e. the grant exists). */
    fun write(context: Context, uuid: String): Boolean = runCatching {
        Settings.Global.putString(context.contentResolver, SECURE_KEY, uuid)
    }.onFailure { Timber.w("TamperSentinel: secure grant missing — write skipped") }
        .getOrDefault(false)
}
