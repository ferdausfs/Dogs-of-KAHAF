package com.guardian.shield

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

// FIX: Removed redundant import of BuildConfig (same package)
@HiltAndroidApp
class GuardianApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}