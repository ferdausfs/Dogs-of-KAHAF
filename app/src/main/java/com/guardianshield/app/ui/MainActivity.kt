package com.guardianshield.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.guardianshield.app.R
import com.guardianshield.app.manager.PinManager
import com.guardianshield.app.service.ProtectionForegroundService
import com.guardianshield.app.ui.admin.PinActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch from splash theme to main theme BEFORE super.onCreate.
        setTheme(R.style.Theme_GuardianShield)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // First-launch PIN setup
        if (!PinManager.hasPin(this)) {
            startActivity(Intent(this, PinActivity::class.java).putExtra("mode", "setup"))
        }

        // Start protection service.
        ContextCompat.startForegroundService(
            this, Intent(this, ProtectionForegroundService::class.java)
        )

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navHost.navController)
    }
}
