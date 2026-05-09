package com.guardian.shield.ui.dashboard

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * v11 (2.1.1) STABILITY PATCH:
 *  • CRITICAL FIX: POST_NOTIFICATIONS is now requested at first launch
 *    on Android 13+. Without it the foreground service silently fails
 *    to show its notification and the OS may kill the service.
 *  • CRITICAL FIX: exportLog() now uses MediaStore on Android 10+
 *    (legacy Environment.getExternalStoragePublicDirectory write fails
 *    silently or throws on scoped-storage devices).
 *  • DEFENSIVE: every startActivity() in click handlers wrapped in
 *    runCatching with toast feedback.
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
                maybeRequestNotificationPermission()
            } else {
                finish()
            }
        }

    private val pinVerifyLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                unlocked = true
                binding.root.visibility = View.VISIBLE
                maybeRequestNotificationPermission()
            } else {
                finishAffinity()
            }
        }

    // v11: runtime POST_NOTIFICATIONS request — required on API 33+
    // for the foreground-service notification to be visible.
    private val notificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "Notifications are required for protection to stay active",
                    Toast.LENGTH_LONG
                ).show()
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
            safeStartActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnSettings.setOnClickListener {
            safeStartActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnPermissions.setOnClickListener {
            safeStartActivity(Intent(this, PermissionsActivity::class.java))
        }
        binding.btnClear.setOnClickListener { vm.clearAll() }

        binding.fabToggle.setOnClickListener { vm.toggleProtection() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.ui.collect { state ->
                    runCatching {
                        adapter.submit(state.recent)
                        binding.tvTodayCount.text = state.todayCount.toString()
                        binding.tvProtectionStatus.text = getString(
                            when {
                                !state.protectionEnabled -> R.string.protection_paused
                                state.protectionActive   -> R.string.protection_active
                                else                     -> R.string.protection_inactive
                            }
                        )
                        binding.tvStatsTotal.text   = state.stats.totalBlocks.toString()
                        binding.tvStatsAi.text      = state.stats.aiBlocks.toString()
                        binding.tvStatsKeyword.text = state.stats.keywordBlocks.toString()
                        binding.tvStatsTopApp.text  = state.stats.topApp ?: "—"
                        val iconRes = if (state.protectionEnabled)
                            R.drawable.ic_shield_on else R.drawable.ic_shield_off
                        binding.fabToggle.setImageResource(iconRes)
                    }.onFailure { Timber.w(it, "Dashboard UI bind failed") }
                }
            }
        }
    }

    private fun safeStartActivity(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure {
                Timber.w(it, "startActivity failed: ${intent.action ?: intent.component}")
                Toast.makeText(this, "Could not open that screen", Toast.LENGTH_SHORT).show()
            }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    /**
     * v11: scoped-storage-aware CSV export.
     *  • Android 10+ → MediaStore (Downloads collection).
     *  • Older       → legacy public Downloads dir.
     */
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
                    val fileName = "guardian_log_${System.currentTimeMillis()}.csv"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        writeViaMediaStore(fileName, csv)
                    } else {
                        @Suppress("DEPRECATION")
                        val downloads = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        )
                        if (!downloads.exists()) downloads.mkdirs()
                        val file = File(downloads, fileName)
                        file.writeText(csv)
                        file.absolutePath
                    }
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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun writeViaMediaStore(fileName: String, csv: String): String {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("MediaStore insert returned null")
        resolver.openOutputStream(uri).use { out ->
            out ?: throw java.io.IOException("Could not open output stream")
            out.write(csv.toByteArray(Charsets.UTF_8))
            out.flush()
        }
        return "Downloads/$fileName"
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
                safeStartActivity(Intent(this, PermissionsActivity::class.java))
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean = runCatching {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.id.contains(packageName) }
    }.getOrDefault(false)
}
