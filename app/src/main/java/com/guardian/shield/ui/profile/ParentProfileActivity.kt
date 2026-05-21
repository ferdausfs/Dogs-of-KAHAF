package com.guardian.shield.ui.profile

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.R
import com.guardian.shield.admin.GuardianDeviceAdminReceiver
import com.guardian.shield.databinding.ActivityProfileBinding
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.ui.navigation.AppBottomNav
import com.guardian.shield.ui.permissions.PermissionsActivity
import com.guardian.shield.ui.settings.SettingsActivity
import com.guardian.shield.ui.setup.PinSetupActivity
import com.guardian.shield.ui.setup.PinVerifyActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ParentProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    @Inject lateinit var pinManager: PinManager

    private val pinLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) finish() else renderState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.cardPin.setOnClickListener {
            startActivity(Intent(this, PinSetupActivity::class.java))
        }
        binding.cardPermissions.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        binding.cardSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.cardAdmin.setOnClickListener {
            requestDeviceAdmin()
        }

        AppBottomNav.bind(this, binding.bottomNav, R.id.nav_profile)

        if (pinManager.isPinSet()) {
            pinLauncher.launch(Intent(this, PinVerifyActivity::class.java))
        } else {
            renderState()
        }
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    private fun renderState() {
        binding.txtPinStatus.text = if (pinManager.isPinSet()) {
            getString(R.string.profile_pin_set)
        } else {
            getString(R.string.profile_pin_not_set)
        }

        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, GuardianDeviceAdminReceiver::class.java)
        binding.txtAdminStatus.text = if (dpm.isAdminActive(admin)) {
            getString(R.string.profile_admin_enabled)
        } else {
            getString(R.string.profile_admin_disabled)
        }
    }

    private fun requestDeviceAdmin() {
        val admin = ComponentName(this, GuardianDeviceAdminReceiver::class.java)
        startActivity(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.profile_admin_explainer)
                )
            }
        )
    }
}
