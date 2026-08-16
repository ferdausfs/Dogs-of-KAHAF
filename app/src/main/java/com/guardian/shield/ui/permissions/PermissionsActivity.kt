package com.guardian.shield.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityPermissionsBinding
import com.guardian.shield.util.PermissionManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* onResume() re-reads the grant state and refreshes the row */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnFixAll.setOnClickListener { fixAllCritical() }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        setupRow(
            row = binding.rowAccessibility,
            icon = binding.iconAccessibility,
            btn = binding.btnAccessibility,
            granted = PermissionManager.isAccessibilityEnabled(this)
        ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        setupRow(
            row = binding.rowOverlay,
            icon = binding.iconOverlay,
            btn = binding.btnOverlay,
            granted = PermissionManager.isOverlayGranted(this)
        ) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }

        setupRow(
            row = binding.rowUsageStats,
            icon = binding.iconUsageStats,
            btn = binding.btnUsageStats,
            granted = PermissionManager.isUsageStatsGranted(this)
        ) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setupRow(
                row = binding.rowNotification,
                icon = binding.iconNotification,
                btn = binding.btnNotification,
                granted = PermissionManager.isNotificationGranted(this)
            ) {
                // Request the runtime permission first; fall back to the app
                // notification settings if the user previously denied it.
                if (!PermissionManager.isNotificationGranted(this)) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    })
                }
            }
        } else {
            binding.rowNotification.visibility = View.GONE
        }

        setupRow(
            row = binding.rowBattery,
            icon = binding.iconBattery,
            btn = binding.btnBattery,
            granted = PermissionManager.isBatteryOptimizationIgnored(this)
        ) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
    }

    private fun setupRow(
        row: View,
        icon: ImageView,
        btn: TextView,
        granted: Boolean,
        onFix: () -> Unit
    ) {
        if (granted) {
            icon.setImageResource(R.drawable.ic_check_circle)
            icon.setColorFilter(getColor(R.color.success))
            btn.text = getString(R.string.permission_granted)
            btn.setTextColor(getColor(R.color.success))
            row.setOnClickListener(null)
            row.isClickable = false
        } else {
            icon.setImageResource(R.drawable.ic_warning)
            icon.setColorFilter(getColor(R.color.error))
            btn.text = getString(R.string.permission_fix)
            btn.setTextColor(getColor(R.color.primary))
            row.setOnClickListener { onFix() }
        }
    }

    private fun fixAllCritical() {
        if (!PermissionManager.isAccessibilityEnabled(this)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return
        }
        if (!PermissionManager.isOverlayGranted(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))); return
        }
        if (!PermissionManager.isBatteryOptimizationIgnored(this)) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
    }
}