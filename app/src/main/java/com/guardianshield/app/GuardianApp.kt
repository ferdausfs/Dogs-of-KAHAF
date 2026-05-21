package com.guardianshield.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.guardianshield.app.data.db.GuardianDatabase
import com.guardianshield.app.data.repo.GuardianRepository
import com.guardianshield.app.util.Constants

/**
 * Application class for Guardian Shield v2.0.
 * Holds singletons (DB, repo) and registers notification channels.
 */
class GuardianApp : Application() {

    val database: GuardianDatabase by lazy { GuardianDatabase.getInstance(this) }
    val repository: GuardianRepository by lazy { GuardianRepository(database) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_PROTECTION,
                "Protection Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Guardian Shield active status" }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_ALERTS,
                "Tamper & Block Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Parent alerts and block notifications" }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_REMINDERS,
                "Islamic Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Quran and Hadith reminders" }
        )
    }

    companion object {
        @Volatile private var instance: GuardianApp? = null
        fun get(): GuardianApp = instance!!
    }
}
