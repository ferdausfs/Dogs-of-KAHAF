package com.kahaf.guardian.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kahaf.guardian.R
import com.kahaf.guardian.databinding.ActivityMainBinding
import com.kahaf.guardian.service.KahafAccessibilityService
import com.kahaf.guardian.service.KahafForegroundService
import com.kahaf.guardian.ui.applist.AppListActivity
import com.kahaf.guardian.ui.common.*
import com.kahaf.guardian.ui.delay.DelayUnlockActivity
import com.kahaf.guardian.ui.pin.PinSetupActivity
import com.kahaf.guardian.ui.settings.SettingsActivity
import com.kahaf.guardian.util.Constants
import com.kahaf.guardian.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
        observeState()
        checkPermissions()
        checkFirstRun()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    navigateToSettings()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupListeners() {
        binding.switchProtection.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewModel.toggleProtection(true)
                KahafForegroundService.start(this)
            } else {
                // Require delay + PIN to disable
                navigateToDelayUnlock(Constants.PURPOSE_DISABLE)
            }
        }

        binding.cardBlockedApps.setOnClickListener {
            val intent = Intent(this, AppListActivity::class.java).apply {
                putExtra("mode", "blocked")
            }
            startActivity(intent)
        }

        binding.cardWhitelistedApps.setOnClickListener {
            val intent = Intent(this, AppListActivity::class.java).apply {
                putExtra("mode", "whitelisted")
            }
            startActivity(intent)
        }

        binding.cardAccessibility.setOnClickListener {
            PermissionHelper.openAccessibilitySettings(this)
        }

        binding.cardOverlay.setOnClickListener {
            PermissionHelper.openOverlaySettings(this)
        }
    }

    private fun observeState() {
        collectFlow(viewModel.protectionState) { state ->
            binding.tvProtectionStatus.text = if (state.isActive) {
                getString(R.string.protection_active)
            } else {
                getString(R.string.protection_inactive)
            }

            binding.tvProtectionStatus.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (state.isActive) R.color.success_green else R.color.error
                )
            )

            // Avoid triggering listener when programmatically setting
            binding.switchProtection.setOnCheckedChangeListener(null)
            binding.switchProtection.isChecked = state.isActive
            binding.switchProtection.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    viewModel.toggleProtection(true)
                    KahafForegroundService.start(this)
                } else {
                    navigateToDelayUnlock(Constants.PURPOSE_DISABLE)
                }
            }

            binding.tvBlockedToday.text = state.blockedTodayCount.toString()
            binding.tvTotalBlocked.text = state.totalBlockedCount.toString()
        }

        collectFlow(viewModel.getBlockedAppsCount()) { count ->
            binding.tvBlockedAppsCount.text = "$count apps blocked"
        }

        collectFlow(viewModel.getWhitelistedAppsCount()) { count ->
            binding.tvWhitelistedAppsCount.text = "$count apps whitelisted"
        }
    }

    private fun updatePermissionStatus() {
        val accessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(this)
        binding.tvAccessibilityStatus.text = if (accessibilityEnabled) "Enabled" else "Disabled"
        binding.tvAccessibilityStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (accessibilityEnabled) R.color.success_green else R.color.error
            )
        )

        val overlayGranted = PermissionHelper.isOverlayPermissionGranted(this)
        binding.tvOverlayStatus.text = if (overlayGranted) "Granted" else "Disabled"
        binding.tvOverlayStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (overlayGranted) R.color.success_green else R.color.error
            )
        )
    }

    private fun checkPermissions() {
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkFirstRun() {
        lifecycleScope.launch {
            viewModel.isPinSet.collect { isPinSet ->
                if (!isPinSet) {
                    startActivity(Intent(this@MainActivity, PinSetupActivity::class.java))
                }
            }
        }
    }

    private fun navigateToSettings() {
        val intent = Intent(this, DelayUnlockActivity::class.java).apply {
            putExtra(Constants.EXTRA_PURPOSE, Constants.PURPOSE_SETTINGS)
        }
        startActivity(intent)
    }

    private fun navigateToDelayUnlock(purpose: String) {
        val intent = Intent(this, DelayUnlockActivity::class.java).apply {
            putExtra(Constants.EXTRA_PURPOSE, purpose)
        }
        startActivity(intent)
    }

    override fun onBackPressed() {
        // Minimize to home instead of closing
        moveTaskToBack(true)
    }
}