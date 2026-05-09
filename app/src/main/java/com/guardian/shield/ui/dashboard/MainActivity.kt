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
 * v15 (2.1.5) STABILITY PATCH 5:
 *  • FIX-1: onResume() no longer starts the foreground service or reloads
 *    rules until the user has authenticated (PIN unlocked). Calling
 *    startForegroundService() while the app is not yet in the foreground
 *    on Android 12+ throws IllegalStateException. The PIN flow runs as
 *    a separate launched activity, which means MainActivity is briefly
 *    in the background — exactly the trigger condition.
 *  • FIX-5: pinManager.isPinSet() is now invoked from a background
 *    coroutine; the lazy SecureStorage init can perform Keystore-bound
 *    work that blocks the main thread on slow devices.
 *
 * v12 (2.1.2):
 *  • DEFENSIVE: notification permission is now also requested when user
 *    re-opens the app and protection is degraded.
 *  • DEFENSIVE: every collect block runCatching-wrapped.
 *  • Permission warning click also offers Permissions screen on first run.
 *
 * v11 (2.1.1):
 *  • POST_NOTIFICATIONS is requested at first launch on Android 13+.
 *  • exportLog() uses MediaStore on Android 10+.
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
            lifecycleScope.launch {
                val pinNowSet = withContext(Dispatchers.IO) {
                    runCatching { pinManager.isPinSet() }.getOrDefault(false)
                }
                if (res.resultCode == RESULT_OK || pinNowSet) {
                    unlocked = true
                    binding.root.visibility = View.VISIBLE
                    maybeRequestNotificationPermission()
                } else {
                    finish()
                }
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

        // v15 (FIX-5): pinManager.isPinSet() may trigger lazy
        // EncryptedSharedPreferences creation, which performs Keystore I/O
        // and can block the main thread for several seconds on broken-
        // Keystore devices. Move the check off the main thread and only
        // launch the PIN activity once we have an answer.
        lifecycleScope.launch {
            val pinSet = withContext(Dispatchers.IO) {
                runCatching { pinManager.isPinSet() }.getOrDefault(false)
            }
            if (!pinSet) {
                runCatching {
                    pinSetupLauncher.launch(Intent(this@MainActivity, PinSetupActivity::class.java))
                }.onFailure { Timber.w(it, "Failed to launch PinSetupActivity") }
            } else {
                runCatching {
                    pinVerifyLauncher.launch(Intent(this@MainActivity, PinVerifyActivity::class.java))
                }.onFailure { Timber.w(it, "Failed to launch PinVerifyActivity") }
            }
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
        binding.btnClear.setOnClickListener { runCatching { vm.clearAll() } }

        binding.fabToggle.setOnClickListener { runCatching { vm.toggleProtection() } }

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

    /**
     * v15 (2.1.5) FIX-1: Only run service/rules logic AFTER the user has
     * authenticated (PIN unlocked). On Android 12+, calling
     * startForegroundService() while the app is not in the foreground
     * (which is briefly the case while the PIN activity is on top)
     * throws IllegalStateException. The alarm-retry path masked this,
     * but the service frequently never started cleanly.
     */
    override fun onResume() {
        super.onResume()
        val active = isAccessibilityEnabled()
        vm.setProtectionActive(active)

        if (unlocked && active) {
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
        if (unlocked) refreshPermissionBanner()
    }

    private fun refreshPermissionBanner() {
        val missing = runCatching { PermissionManager.missingCritical(this) }
            .getOrDefault(emptyList())
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
