package com.guardian.shield.ui.dashboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
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

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: DashboardViewModel by viewModels()
    @Inject lateinit var pinManager: PinManager
    @Inject lateinit var rulesEngine: RulesEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!pinManager.isPinSet()) {
            startActivity(Intent(this, PinSetupActivity::class.java))
        } else {
            startActivity(Intent(this, PinVerifyActivity::class.java))
        }

        val adapter = BlockEventAdapter()
        binding.rvEvents.layoutManager = LinearLayoutManager(this)
        binding.rvEvents.adapter = adapter

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnSettings.setOnClickListener {
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
