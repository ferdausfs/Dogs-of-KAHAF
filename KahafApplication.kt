package com.kahaf.guardian

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KahafApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // App initialization
        // No heavy work here - use lazy initialization
    }
}