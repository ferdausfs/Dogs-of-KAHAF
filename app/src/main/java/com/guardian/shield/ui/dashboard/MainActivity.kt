package com.guardian.shield.ui.dashboard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.R
import com.guardian.shield.admin.GuardianDeviceAdminReceiver
import com.guardian.shield.databinding.ActivityMainBinding
import com.guardian.shield.service.blocker.GuardianForegroundService
import com.guardian.shield.service.detection.TimeLockManager
import com.guardian.shield.ui.permissions.PermissionsActivity
import com.guardian.shield.ui.settings.SettingsActivity
import com.guardian.shield.util.PermissionManager
import com.guardian.shield.viewmodel.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var adapter: BlockEventAdapter

    @Inject lateinit var timeLockManager: TimeLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = BlockEventAdapter(
            onDelete = { viewModel.deleteEvent(it.id) }
        )
        binding.recyclerRecent.layoutManager = LinearLayoutManager(this)
        binding.recyclerRecent.adapter = adapter

        binding.fabToggle.setOnClickListener {
            handleToggle()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnPermissions.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }

        startForegroundServiceIfNeeded()
        checkDeviceAdmin()
    }

    override fun onResume() {
        super.onResume()
        viewModel.setProtectionActive(PermissionManager.isAccessibilityEnabled(this))
    }

    private fun handleToggle() {
        timeLockManager.clearIfExpired()
        if (timeLockManager.isLocked()) {
            Snackbar.make(
                binding.root,
                "🔒 Commitment Lock active — ${timeLockManager.getRemainingFormatted()}",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        viewModel.toggleProtection()
    }



    private fun startForegroundServiceIfNeeded() {
        runCatching { GuardianForegroundService.start(this) }
    }

    private fun checkDeviceAdmin() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, GuardianDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Uninstall Protection")
                .setMessage(
                    "App কে uninstall থেকে রক্ষা করতে Device Admin enable করুন।\n\n" +
                    "এটা ছাড়া যেকেউ Guardian Shield delete করে protection বন্ধ করতে পারবে।"
                )
                .setCancelable(false)
                .setPositiveButton("Enable করুন") { _, _ ->
                    runCatching {
                        startActivity(
                            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                                putExtra(
                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "Guardian Shield কে uninstall থেকে রক্ষা করতে এটা প্রয়োজন।"
                                )
                            }
                        )
                    }
                }
                .setNegativeButton("পরে") { _, _ -> }
                .show()
        }
    }

    private fun render(state: com.guardian.shield.viewmodel.DashboardUiState) {
        when {
            !state.protectionActive -> {
                binding.txtStatusTitle.text = getString(R.string.status_service_off)
                binding.txtStatusSubtitle.text = getString(R.string.status_service_off_sub)
                binding.imgShield.setImageResource(R.drawable.ic_shield_off)
                binding.statusCard.setStrokeColor(getColor(R.color.error))
            }
            !state.protectionEnabled -> {
                binding.txtStatusTitle.text = getString(R.string.status_paused)
                binding.txtStatusSubtitle.text = getString(R.string.status_paused_sub)
                binding.imgShield.setImageResource(R.drawable.ic_shield_off)
                binding.statusCard.setStrokeColor(getColor(R.color.on_surface_dim))
            }
            else -> {
                binding.txtStatusTitle.text = getString(R.string.status_active)
                binding.txtStatusSubtitle.text = getString(
                    R.string.status_active_sub_fmt,
                    state.todayCount
                )
                binding.imgShield.setImageResource(R.drawable.ic_shield_on)
                binding.statusCard.setStrokeColor(getColor(R.color.primary))
            }
        }

        binding.txtStatTotal.text = state.stats.totalBlocks.toString()
        binding.txtStatAi.text = state.stats.aiBlocks.toString()
        binding.txtStatKeyword.text = state.stats.keywordBlocks.toString()
        binding.txtStatTopApp.text = state.stats.topApp?.substringAfterLast('.')?.take(8) ?: "—"

        binding.fabToggle.setImageResource(
            if (state.protectionEnabled) R.drawable.ic_shield_on else R.drawable.ic_shield_off
        )

        adapter.submit(state.recent)
        binding.txtEmpty.visibility =
            if (state.recent.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_dashboard, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> { exportCsv(); true }
            R.id.action_clear -> { confirmClear(); true }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_logs)
            .setMessage(R.string.clear_logs_msg)
            .setPositiveButton(R.string.confirm) { _, _ -> viewModel.clearAll() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportCsv() {
        lifecycleScope.launch {
            try {
                val events = viewModel.getAllEvents()
                val csv = buildString {
                    append("id,packageName,reason,matchedTerm,timestamp,iso\n")
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    events.forEach { ev ->
                        append(ev.id).append(',')
                        append(ev.packageName).append(',')
                        append(ev.reason.name).append(',')
                        append((ev.matchedTerm ?: "").replace(",", " ")).append(',')
                        append(ev.timestamp).append(',')
                        append(fmt.format(Date(ev.timestamp))).append('\n')
                    }
                }
                val name = "guardian_blocks_${System.currentTimeMillis()}.csv"
                val file = withContext(Dispatchers.IO) {
                    val dir = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    } else {
                        getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                    }
                    if (!dir.exists()) dir.mkdirs()
                    val f = File(dir, name)
                    f.writeText(csv)
                    f
                }
                Snackbar.make(
                    binding.root,
                    getString(R.string.csv_saved, file.absolutePath),
                    Snackbar.LENGTH_LONG
                ).show()
            } catch (t: Throwable) {
                Timber.e(t)
                Snackbar.make(binding.root, "Export failed", Snackbar.LENGTH_SHORT).show()
            }
        }
    }
}
