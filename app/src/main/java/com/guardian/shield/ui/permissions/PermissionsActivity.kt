package com.guardian.shield.ui.permissions

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.guardian.shield.R
import com.guardian.shield.admin.GuardianDeviceAdminReceiver
import com.guardian.shield.databinding.ActivityPermissionsBinding
import com.guardian.shield.util.PermissionManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding

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
        binding.permissionsContainer.removeAllViews()

        addPermRow("Accessibility Service", PermissionManager.isAccessibilityEnabled(this), true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        addPermRow("Display Over Other Apps", PermissionManager.hasOverlayPermission(this), true) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        addPermRow("Usage Stats Access", PermissionManager.hasUsageStatsPermission(this), true) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addPermRow("Notification Permission", PermissionManager.hasNotificationPermission(this), true) {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                })
            }
        }

        // ✅ Battery optimization — most important for always-active
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        addPermRow("Battery Optimization (Unrestricted)", pm.isIgnoringBatteryOptimizations(packageName), true) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }

        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, GuardianDeviceAdminReceiver::class.java)
        addPermRow("Device Admin (Uninstall Protection)", dpm.isAdminActive(admin), false) {
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Uninstall protection এর জন্য প্রয়োজন")
            })
        }
    }

    private fun addPermRow(label: String, granted: Boolean, critical: Boolean, onFix: () -> Unit) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_permission_row, binding.permissionsContainer, false)
        row.findViewById<TextView>(R.id.txtPermLabel).text = label
        val btnFix = row.findViewById<MaterialButton>(R.id.btnFix)
        val badge = row.findViewById<TextView>(R.id.txtBadge)
        if (granted) {
            badge.text = "✅"
            btnFix.visibility = View.GONE
        } else {
            badge.text = if (critical) "❌" else "⚠️"
            btnFix.visibility = View.VISIBLE
            btnFix.setOnClickListener { onFix() }
        }
        binding.permissionsContainer.addView(row)
    }

    private fun fixAllCritical() {
        if (!PermissionManager.isAccessibilityEnabled(this)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return
        }
        if (!PermissionManager.hasOverlayPermission(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
    }
}