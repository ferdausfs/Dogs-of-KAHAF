package com.guardian.shield.util

import android.content.Context
import android.content.Intent
import android.os.Build
import com.guardian.shield.BuildConfig
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PHASE 1b (v3.5.0) — Crash/ANR reporting.
 *
 * Two layers, deliberately independent:
 *
 * 1. **Firebase Crashlytics (optional, owner-gated).** The
 *    `firebase-crashlytics` SDK auto-initialises at process start ONLY when
 *    the build included a real `app/google-services.json` (the Gradle gate in
 *    `app/build.gradle.kts` then sets [BuildConfig.CRASHLYTICS_CONFIGURED]).
 *    When configured, Crashlytics' own uncaught-exception handler reports the
 *    crash to the owner's Firebase console. We never call any Crashlytics API
 *    unconditionally: every use is wrapped in `runCatching` and effectively
 *    guarded by the configured flag. Nothing extra (no user id, no custom
 *    keys, no analytics) is attached — only Crashlytics' default payload.
 *
 * 2. **Local crash log (always on, fully offline).** [install] registers an
 *    [Thread.UncaughtExceptionHandler] that appends a plain-text record —
 *    timestamp, app version, device model, Android release, thread name and
 *    the exception stack trace — to `filesDir/crash_log.txt` (capped, newest
 *    kept), then delegates to the previously installed handler so the
 *    system's normal crash behaviour (and Crashlytics, when present) is
 *    preserved. The user can export the log from Help → Export diagnostics.
 *
 * PII: the local log contains no personal data (no identifiers, no content,
 * no screenshots) — device model and OS version only.
 */
object GuardianCrashHandler {

    private const val FILE_NAME = "crash_log.txt"
    private const val MAX_RECORDS = 30
    private const val RECORD_SEPARATOR =
        "\n--- crash -------------------------------------------------------\n"

    @Volatile private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                persistLocally(appContext, thread, throwable)
            } catch (t: Throwable) {
                Timber.w(t, "Local crash persist failed")
            }
            // Chain: the previous handler is Crashlytics' (when configured) or
            // the system default — either way normal crash handling continues.
            previous?.uncaughtException(thread, throwable)
        }
        Timber.i(
            "Crash handler installed (crashlyticsConfigured=${BuildConfig.CRASHLYTICS_CONFIGURED})"
        )
    }

    /** Append one crash record, trimming the file to the newest [MAX_RECORDS]. */
    private fun persistLocally(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val record = buildString {
            append("time=").append(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            ).append('\n')
            append("app=").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" android=").append(Build.VERSION.RELEASE)
                .append(" sdk=").append(Build.VERSION.SDK_INT).append('\n')
            append("thread=").append(thread.name).append('\n')
            append(sw.toString())
        }
        val f = File(context.filesDir, FILE_NAME)
        val existing = if (f.exists()) {
            runCatching { f.readText(Charsets.UTF_8) }.getOrDefault("")
        } else ""
        val records = (existing.split(RECORD_SEPARATOR)
            .filter { it.isNotBlank() } + record)
            .takeLast(MAX_RECORDS)
        f.writeText(records.joinToString(RECORD_SEPARATOR), Charsets.UTF_8)
    }

    /** Read the local crash log (diagnostics export). Empty string when none. */
    fun readLog(context: Context): String = runCatching {
        File(context.filesDir, FILE_NAME).takeIf { it.exists() }
            ?.readText(Charsets.UTF_8).orEmpty()
    }.getOrDefault("")

    /** A plain-text diagnostics bundle used by the Help → share action. */
    fun buildDiagnosticsBundle(context: Context): String {
        val log = readLog(context)
        return buildString {
            append("Guardian Shield diagnostics\n")
            append("app=").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("crashlytics_configured=").append(BuildConfig.CRASHLYTICS_CONFIGURED).append('\n')
            append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" android=").append(Build.VERSION.RELEASE).append('\n')
            append('\n')
            if (log.isBlank()) append("(no local crash records)")
            else append(log)
        }
    }

    /** Share-sheet intent carrying the diagnostics bundle (user sends it). */
    fun buildShareIntent(context: Context): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Guardian Shield diagnostics")
            putExtra(Intent.EXTRA_TEXT, buildDiagnosticsBundle(context))
        }
}
