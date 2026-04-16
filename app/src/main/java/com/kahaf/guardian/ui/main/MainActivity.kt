package com.kahaf.guardian.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kahaf.guardian.R
import com.kahaf.guardian.databinding.ActivityMainBinding
import com.kahaf.guardian.service.KahafForegroundService
import com.kahaf.guardian.ui.applist.AppListActivity
import com.kahaf.guardian.ui.common.*
import com.kahaf.guardian.ui.delay.DelayUnlockActivity
import com.kahaf.guardian.ui.pin.PinSetupActivity
import com.kahaf.guardian.util.Constants
import com.kahaf.guardian.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setOnMenuItemClickListener { if (it.itemId == R.id.action_settings) { startActivity(Intent(this, DelayUnlockActivity::class.java).putExtra(Constants.EXTRA_PURPOSE, Constants.PURPOSE_SETTINGS)); true } else false }

        binding.switchProtection.setOnCheckedChangeListener { _, checked ->
            if (checked) { vm.toggleProtection(true); KahafForegroundService.start(this) }
            else startActivity(Intent(this, DelayUnlockActivity::class.java).putExtra(Constants.EXTRA_PURPOSE, Constants.PURPOSE_DISABLE))
        }

        binding.cardBlockedApps.setOnClickListener { startActivity(Intent(this, AppListActivity::class.java).putExtra("mode", "blocked")) }
        binding.cardWhitelistedApps.setOnClickListener { startActivity(Intent(this, AppListActivity::class.java).putExtra("mode", "whitelisted")) }
        binding.cardAccessibility.setOnClickListener { PermissionHelper.openAccessibilitySettings(this) }
        binding.cardOverlay.setOnClickListener { PermissionHelper.openOverlaySettings(this) }

        collectFlow(vm.protectionState) { s ->
            binding.tvProtectionStatus.text = if (s.isActive) getString(R.string.protection_active) else getString(R.string.protection_inactive)
            binding.tvProtectionStatus.setTextColor(ContextCompat.getColor(this, if (s.isActive) R.color.success_green else R.color.error))
            binding.switchProtection.setOnCheckedChangeListener(null)
            binding.switchProtection.isChecked = s.isActive
            binding.switchProtection.setOnCheckedChangeListener { _, checked ->
                if (checked) { vm.toggleProtection(true); KahafForegroundService.start(this) }
                else startActivity(Intent(this, DelayUnlockActivity::class.java).putExtra(Constants.EXTRA_PURPOSE, Constants.PURPOSE_DISABLE))
            }
            binding.tvBlockedToday.text = s.blockedTodayCount.toString()
            binding.tvTotalBlocked.text = s.totalBlockedCount.toString()
        }
        collectFlow(vm.getBlockedAppsCount()) { binding.tvBlockedAppsCount.text = "$it apps blocked" }
        collectFlow(vm.getWhitelistedAppsCount()) { binding.tvWhitelistedAppsCount.text = "$it apps whitelisted" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

        lifecycleScope.launch { vm.isPinSet.collect { if (!it) startActivity(Intent(this@MainActivity, PinSetupActivity::class.java)) } }
    }

    override fun onResume() {
        super.onResume()
        val acc = PermissionHelper.isAccessibilityServiceEnabled(this)
        binding.tvAccessibilityStatus.text = if (acc) "Enabled" else "Disabled"
        binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, if (acc) R.color.success_green else R.color.error))
        val ov = PermissionHelper.isOverlayPermissionGranted(this)
        binding.tvOverlayStatus.text = if (ov) "Granted" else "Disabled"
        binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, if (ov) R.color.success_green else R.color.error))
    }

    @Deprecated("Deprecated") override fun onBackPressed() { moveTaskToBack(true) }
}
