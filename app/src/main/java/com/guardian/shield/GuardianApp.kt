package com.guardian.shield

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * v11 (2.1.1) STABILITY PATCH:
 *  • Global uncaught-exception handler installed in DEBUG builds so any
 *    background-thread crash is logged with full stack to Logcat (was
 *    being silently swallowed on some devices).
 *  • In RELEASE we still let Android handle the crash normally, so Play
 *    Console / Crashlytics receive it.
 */
@HiltAndroidApp
class GuardianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            installCrashLogger()
        }
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                Timber.e(throwable, "UNCAUGHT on thread ${thread.name}")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
