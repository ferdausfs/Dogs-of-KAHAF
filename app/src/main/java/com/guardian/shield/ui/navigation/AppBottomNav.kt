package com.guardian.shield.ui.navigation

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.guardian.shield.R
import com.guardian.shield.ui.dashboard.MainActivity
import com.guardian.shield.ui.profile.ParentProfileActivity
import com.guardian.shield.ui.settings.ActivityLogActivity
import com.guardian.shield.ui.settings.SettingsActivity

object AppBottomNav {

    fun bind(
        activity: AppCompatActivity,
        bottomNavigationView: BottomNavigationView,
        selectedItemId: Int
    ) {
        bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true

            val intent = when (item.itemId) {
                R.id.nav_home -> Intent(activity, MainActivity::class.java)
                R.id.nav_activity -> Intent(activity, ActivityLogActivity::class.java)
                R.id.nav_settings -> Intent(activity, SettingsActivity::class.java)
                R.id.nav_profile -> Intent(activity, ParentProfileActivity::class.java)
                else -> null
            }

            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                activity.startActivity(it)
                activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                true
            } ?: false
        }
        bottomNavigationView.selectedItemId = selectedItemId
    }
}
