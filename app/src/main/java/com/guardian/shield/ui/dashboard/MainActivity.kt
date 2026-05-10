package com.guardian.shield.ui.dashboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.guardian.shield.R
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.databinding.ActivityMainBinding
import com.guardian.shield.service.blocker.GuardianForegroundService
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.service.detection.RulesEngine
import com.guardian.shield.ui.permissions.PermissionsActivity
import com.guardian.shield.ui.settings.SettingsActivity
import com.guardian.shield.ui.setup.PinSetupActivity
import com.guardian.shield.ui.setup.PinVerifyActivity
import com.guardian.shield.util.PermissionManager
import com.guardian.shield.viewmodel.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * v9 (2.0.0):
 *  • P4-B → renders block-stats card (totalBlocks / aiBlocks / kwBlocks / topApp).
 *  • P4-C → FAB toggles master protection switch.
 *  • P4-D → "Export Log" menu writes CSV to public Downloads.
 *
 * Earlier v8 BUG-12 still applies (cached rules version skip on resume).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: DashboardViewModel by viewModels()
    @Inject lateinit var pinManager: PinManager
    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var prefs: GuardianPreferences

    private var unlocked = false
    private var cachedRulesVersion: Int = -1

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
            runCatching {
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        binding.btnSettings.setOnClickListener {
            runCatching { startActivity(Intent(this, SettingsActivity::class.java)) }
        }
        binding.btnPermissions.setOnClickListener {
            runCatching { startActivity(Intent(this, PermissionsActivity::class.java)) }
        }
        binding.btnClear.setOnClickListener { vm.clearAll() }

        // P4-C: FAB → master toggle.
        binding.fabToggle.setOnClickListener { vm.toggleProtection() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.ui.collect { state ->
                    adapter.submit(state.recent)
                    binding.tvTodayCount.text = state.todayCount.toString()
                    binding.tvProtectionStatus.text = getString(
                        when {
                            !state.protectionEnabled -> R.string.protection_paused
                            state.protectionActive   -> R.string.protection_active
                            else                     -> R.string.protection_inactive
                        }
                    )
                    // P4-B: stats card.
                    binding.tvStatsTotal.text   = state.stats.totalBlocks.toString()
                    binding.tvStatsAi.text      = state.stats.aiBlocks.toString()
                    binding.tvStatsKeyword.text = state.stats.keywordBlocks.toString()
                    binding.tvStatsTopApp.text  = state.stats.topApp ?: "—"
                    // P4-C: FAB icon.
                    val iconRes = if (state.protectionEnabled)
                        R.drawable.ic_shield_on else R.drawable.ic_shield_off
                    runCatching { binding.fabToggle.setImageResource(iconRes) }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_dashboard, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_log -> {
                exportLog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** P4-D: write all block events to a CSV in public Downloads. */
    private fun exportLog() {
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val events = vm.getAllEvents()
                    val csv = buildString {
                        appendLine("timestamp,package,reason,matched_term")
                        events.forEach { e ->
                            val safeTerm = (e.matchedTerm ?: "").replace(',', ' ').replace('\n', ' ')
                            appendLine("${e.timestamp},${e.packageName},${e.reason.name},$safeTerm")
                        }
                    }
                    val downloads = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )
                    if (!downloads.exists()) downloads.mkdirs()
                    val file = File(downloads, "guardian_log_${System.currentTimeMillis()}.csv")
                    file.writeText(csv)
                    file.absolutePath
                }
            }
            outcome.onSuccess { path ->
                Toast.makeText(this@MainActivity, "Exported: $path", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(
                    this@MainActivity,
                    "Export failed: ${it.message ?: "unknown"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val active = isAccessibilityEnabled()
        vm.setProtectionActive(active)
        if (active) {
            GuardianForegroundService.start(this)
            lifecycleScope.launch {
                runCatching {
                    val current = prefs.currentRulesVersion()
                    if (current != cachedRulesVersion) {
                        cachedRulesVersion = current
                        rulesEngine.reload()
                    }
                }
            }
        }
        binding.btnEnableAccessibility.text = getString(
            if (active) R.string.accessibility_enabled else R.string.enable_accessibility
        )
        refreshPermissionBanner()
    }

    private fun refreshPermissionBanner() {
        val missing = PermissionManager.missingCritical(this)
        if (missing.isEmpty()) {
            binding.tvPermissionWarning.visibility = View.GONE
        } else {
            binding.tvPermissionWarning.visibility = View.VISIBLE
            binding.tvPermissionWarning.text =
                "⚠ ${missing.size} permission(s) missing — tap to fix"
            binding.tvPermissionWarning.setOnClickListener {
                runCatching { startActivity(Intent(this, PermissionsActivity::class.java)) }
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.id.contains(packageName) }
    }
}
