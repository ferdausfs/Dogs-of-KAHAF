package com.guardian.shield.ui.dashboard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.guardian.shield.R
import com.guardian.shield.admin.GuardianDeviceAdminReceiver
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.databinding.ActivityMainBinding
import com.guardian.shield.service.blocker.GuardianForegroundService
import com.guardian.shield.ui.activitylog.ActivityLogActivity
import com.guardian.shield.ui.dashboard.fragments.DashboardFragment
import com.guardian.shield.ui.onboarding.OnboardingActivity
import com.guardian.shield.ui.settings.SettingsActivity
import com.guardian.shield.util.PermissionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject lateinit var guardianPrefs: GuardianPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        lifecycleScope.launch {
            try {
                val isFirstRun = guardianPrefs.firstRun.first()
                if (isFirstRun) {
                    startActivity(OnboardingActivity.launchIntent(this@MainActivity))
                    finish()
                    return@launch
                }
            } catch (t: Throwable) { Timber.e(t, "firstRun check failed") }
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    loadFragment(DashboardFragment())
                    true
                }
                R.id.nav_logs -> {
                    // Temporarily keep activity or move to fragment later
                    startActivity(Intent(this, ActivityLogActivity::class.java))
                    false
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    false
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }

        startForegroundServiceIfNeeded()
        checkDeviceAdmin()
        checkBatteryOptimization()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun startForegroundServiceIfNeeded() {
        runCatching { GuardianForegroundService.start(this) }
    }

    private fun checkBatteryOptimization() {
        if (!PermissionManager.isBatteryOptimizationIgnored(this)) {
            AlertDialog.Builder(this)
                .setTitle("🔋 Stability Fix")
                .setMessage(
                    "Guardian Shield ব্যাকগ্রাউন্ডে বন্ধ হয়ে যাওয়া রোধ করতে Battery Optimization বন্ধ করুন।"
                )
                .setCancelable(false)
                .setPositiveButton("Fix করুন") { _, _ ->
                    PermissionManager.openBatteryOptimizationSettings(this)
                }
                .setNegativeButton("পরে") { _, _ -> }
                .show()
        }
    }

    private fun checkDeviceAdmin() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, GuardianDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Uninstall Protection")
                .setMessage("App কে uninstall থেকে রক্ষা করতে Device Admin enable করুন।")
                .setCancelable(false)
                .setPositiveButton("Enable করুন") { _, _ ->
                    runCatching {
                        startActivity(
                            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                            }
                        )
                    }
                }
                .setNegativeButton("পরে") { _, _ -> }
                .show()
        }
    }
}
