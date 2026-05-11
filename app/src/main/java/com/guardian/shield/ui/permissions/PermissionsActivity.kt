package com.guardian.shield.ui.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityPermissionsBinding
import com.guardian.shield.util.PermissionManager

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* refresh on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rowAccessibility.setOnClickListener {
            PermissionManager.openAccessibilitySettings(this)
        }
        binding.rowUsageStats.setOnClickListener {
            PermissionManager.openUsageAccessSettings(this)
        }
        binding.rowOverlay.setOnClickListener {
            PermissionManager.openOverlaySettings(this)
        }
        binding.rowNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                runCatching {
                    startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:$packageName")))
                }
            }
        }
        binding.rowBattery.setOnClickListener {
            PermissionManager.openBatteryOptimizationSettings(this)
        }
        binding.btnFixAll.setOnClickListener {
            if (!PermissionManager.isAccessibilityEnabled(this)) {
                PermissionManager.openAccessibilitySettings(this)
            } else if (!PermissionManager.isOverlayGranted(this)) {
                PermissionManager.openOverlaySettings(this)
            } else if (!PermissionManager.isBatteryOptimizationIgnored(this)) {
                PermissionManager.openBatteryOptimizationSettings(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        setRow(binding.iconAccessibility, binding.btnAccessibility,
            PermissionManager.isAccessibilityEnabled(this))
        setRow(binding.iconUsageStats, binding.btnUsageStats,
            PermissionManager.isUsageStatsGranted(this))
        setRow(binding.iconOverlay, binding.btnOverlay,
            PermissionManager.isOverlayGranted(this))
        setRow(binding.iconNotification, binding.btnNotification,
            PermissionManager.isNotificationGranted(this))
        setRow(binding.iconBattery, binding.btnBattery,
            PermissionManager.isBatteryOptimizationIgnored(this))
    }

    private fun setRow(icon: android.widget.ImageView, btn: android.widget.TextView, granted: Boolean) {
        if (granted) {
            icon.setImageResource(R.drawable.ic_check_circle)
            icon.setColorFilter(getColor(R.color.success))
            btn.text = getString(R.string.permission_granted)
            btn.setTextColor(getColor(R.color.success))
        } else {
            icon.setImageResource(R.drawable.ic_warning)
            icon.setColorFilter(getColor(R.color.error))
            btn.text = getString(R.string.permission_fix)
            btn.setTextColor(getColor(R.color.error))
        }
    }
}
