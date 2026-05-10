package com.guardian.shield

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * v12 (2.1.2) FULL OPTIMISATION:
 *  • RELEASE builds now plant a *release-safe* Timber tree that swallows
 *    DEBUG / VERBOSE and only logs WARN+ via android.util.Log. Previously
 *    release builds had no Timber tree at all — `Timber.e(...)` calls
 *    inside `runCatching` recovery paths were silently dropped, making
 *    crashes invisible to OEM bug reports.
 *  • Crash logger always installed (was: DEBUG only). Catches any
 *    background-thread crash and routes through Timber, then re-delegates
 *    to the platform's default handler so Play Console / Crashlytics still
 *    receive it.
 */
@HiltAndroidApp
class GuardianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
        installCrashLogger()
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                Timber.e(throwable, "UNCAUGHT on thread ${thread.name}")
            }
            // Always delegate to the OS handler so the crash is reported.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * v12: minimal release-safe tree. Drops DEBUG/VERBOSE, forwards
     * INFO/WARN/ERROR to android.util.Log so they appear in `adb logcat`
     * and OEM bug reports without exposing internal call sites.
     */
    private class ReleaseTree : Timber.Tree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean =
            priority >= android.util.Log.INFO

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (!isLoggable(tag, priority)) return
            val safeTag = tag ?: "GuardianShield"
            if (t != null) {
                android.util.Log.println(priority, safeTag, "$message\n${android.util.Log.getStackTraceString(t)}")
            } else {
                android.util.Log.println(priority, safeTag, message)
            }
        }
    }
}
