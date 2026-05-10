package com.guardian.shield.ui.dashboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityMainBinding
import com.guardian.shield.service.blocker.GuardianForegroundService
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.service.detection.RulesEngine
import com.guardian.shield.ui.settings.SettingsActivity
import com.guardian.shield.ui.setup.PinSetupActivity
import com.guardian.shield.ui.setup.PinVerifyActivity
import com.guardian.shield.viewmodel.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX-LOG (vs original):
 *  - BUG #5: PIN gate did not actually protect anything — MainActivity rendered
 *            FIRST, then PinVerifyActivity was launched on top. Anyone could press
 *            HOME and re-enter without verifying. Now:
 *               • If no PIN set → PinSetup is launched as ActivityResult; we keep
 *                 the root view hidden until result returns.
 *               • If PIN set → PinVerify is launched as ActivityResult; root view
 *                 is hidden until success. On cancellation we finish().
 *            The dashboard UI is genuinely gated.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: DashboardViewModel by viewModels()
    @Inject lateinit var pinManager: PinManager
    @Inject lateinit var rulesEngine: RulesEngine

    private var unlocked = false

    private val pinSetupLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK || pinManager.isPinSet()) {
                unlocked = true
                binding.root.visibility = View.VISIBLE
            } else {
                finish()
            }
        }

    private val pinVerifyLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                unlocked = true
                binding.root.visibility = View.VISIBLE
            } else {
                finishAffinity()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // BUG #5 fix — hide UI until PIN is verified.
        binding.root.visibility = View.INVISIBLE

        if (!pinManager.isPinSet()) {
            pinSetupLauncher.launch(Intent(this, PinSetupActivity::class.java))
        } else {
            pinVerifyLauncher.launch(Intent(this, PinVerifyActivity::class.java))
        }

        val adapter = BlockEventAdapter()
        binding.rvEvents.layoutManager = LinearLayoutManager(this)
        binding.rvEvents.adapter = adapter

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnSettings.setOnClickListener {
            // Settings still requires PIN (re-verify each time) — see SettingsActivity.
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnClear.setOnClickListener { vm.clearAll() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.ui.collect { state ->
                    adapter.submit(state.recent)
                    binding.tvTodayCount.text = state.todayCount.toString()
                    binding.tvProtectionStatus.text = getString(
                        if (state.protectionActive) R.string.protection_active else R.string.protection_inactive
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val active = isAccessibilityEnabled()
        vm.setProtectionActive(active)
        if (active) {
            GuardianForegroundService.start(this)
            lifecycleScope.launch { rulesEngine.reload() }
        }
        binding.btnEnableAccessibility.text = getString(
            if (active) R.string.accessibility_enabled else R.string.enable_accessibility
        )
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.id.contains(packageName) }
    }
}
