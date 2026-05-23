package com.guardian.shield.ui.dashboard

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
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
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.databinding.ActivityMainBinding
import com.guardian.shield.service.blocker.GuardianForegroundService
import com.guardian.shield.service.detection.TimeLockManager
import com.guardian.shield.ui.navigation.AppBottomNav
import com.guardian.shield.ui.permissions.PermissionsActivity
import com.guardian.shield.ui.settings.ActivityLogActivity
import com.guardian.shield.ui.settings.SettingsActivity
import com.guardian.shield.ui.setup.OnboardingActivity
import com.guardian.shield.util.PermissionManager
import com.guardian.shield.viewmodel.DashboardUiState
import com.guardian.shield.viewmodel.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
    @Inject lateinit var prefs: GuardianPreferences

    private var shieldPulseSet: AnimatorSet? = null
    private var lastProtectionEnabled: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = BlockEventAdapter(
            pm = packageManager,
            onDelete = { viewModel.deleteEvent(it.id) }
        )
        binding.recyclerRecent.layoutManager = LinearLayoutManager(this)
        binding.recyclerRecent.adapter = adapter

        binding.fabToggle.setOnClickListener { handleToggle() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_bottom, R.anim.fade_out)
        }
        binding.btnPermissions.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_bottom, R.anim.fade_out)
        }
        binding.btnViewAll.setOnClickListener {
            startActivity(Intent(this, ActivityLogActivity::class.java))
        }

        AppBottomNav.bind(this, binding.bottomNav, R.id.nav_home)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }

        lifecycleScope.launch {
            if (prefs.firstRun.first()) {
                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                finish()
            } else {
                startForegroundServiceIfNeeded()
                checkDeviceAdmin()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setProtectionActive(PermissionManager.isAccessibilityEnabled(this))
    }

    private fun handleToggle() {
        timeLockManager.clearIfExpired()
        if (timeLockManager.isLocked() || timeLockManager.isInCooldown()) {
            val shakeAnim = AnimatorInflater.loadAnimator(this, R.animator.lock_shake)
            shakeAnim.setTarget(binding.imgShield)
            shakeAnim.start()
            Snackbar.make(
                binding.root,
                getString(R.string.commitment_lock_active_fmt, timeLockManager.getRemainingFormatted()),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        viewModel.toggleProtection()
    }

    private fun render(state: DashboardUiState) {
        val wasEnabled = lastProtectionEnabled
        lastProtectionEnabled = state.protectionEnabled

        when {
            !state.protectionActive -> renderServiceOffState()
            !state.protectionEnabled -> renderPausedState()
            else -> renderActiveState(state.todayCount)
        }

        if (wasEnabled != null && wasEnabled != state.protectionEnabled) {
            binding.statusCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
        }

        animateNumber(binding.txtStatTotal, state.stats.totalBlocks)
        animateNumber(binding.txtStatAi, state.stats.aiBlocks)
        animateNumber(binding.txtStatKeyword, state.stats.keywordBlocks)
        binding.txtStatTopApp.text = state.stats.topApp
            ?.substringAfterLast('.')
            ?.replaceFirstChar { it.uppercase() }
            ?.take(10)
            ?: "—"
        binding.txtTodayActivity.text = getString(
            R.string.today_activity_summary_fmt,
            state.stats.totalBlocks,
            state.stats.aiBlocks
        )

        adapter.submit(state.recent)
        binding.txtEmpty.visibility = if (state.recent.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderServiceOffState() {
        binding.txtStatusTitle.text = getString(R.string.status_service_off)
        binding.txtStatusSubtitle.text = getString(R.string.status_service_off_sub)
        binding.imgShield.setImageResource(R.drawable.ic_shield_off)
        binding.shieldGlow.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.on_surface_dim))
        binding.statusCard.setBackgroundResource(R.drawable.bg_status_paused)
        binding.statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.on_surface_dim))
        binding.txtStatusDot.text = getString(R.string.status_service_off_short)
        binding.txtStatusDot.setTextColor(getColor(R.color.on_surface_dim))
        binding.fabToggle.text = getString(R.string.action_enable)
        binding.fabToggle.setIconResource(R.drawable.ic_shield_on)
        binding.fabToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.on_surface_dim))
        stopShieldPulse()
    }

    private fun renderPausedState() {
        binding.txtStatusTitle.text = getString(R.string.status_paused)
        binding.txtStatusSubtitle.text = getString(R.string.status_paused_sub)
        binding.imgShield.setImageResource(R.drawable.ic_shield_off)
        binding.shieldGlow.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.secondary))
        binding.statusCard.setBackgroundResource(R.drawable.bg_status_paused)
        binding.statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.secondary))
        binding.txtStatusDot.text = getString(R.string.status_paused_short)
        binding.txtStatusDot.setTextColor(getColor(R.color.secondary))
        binding.fabToggle.text = getString(R.string.action_resume)
        binding.fabToggle.setIconResource(R.drawable.ic_shield_on)
        binding.fabToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.success))
        stopShieldPulse()
    }

    private fun renderActiveState(todayCount: Int) {
        binding.txtStatusTitle.text = getString(R.string.status_active)
        binding.txtStatusSubtitle.text = getString(R.string.status_active_sub_fmt, todayCount)
        binding.imgShield.setImageResource(R.drawable.ic_shield_on)
        binding.shieldGlow.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.primary))
        binding.statusCard.setBackgroundResource(R.drawable.bg_status_active)
        binding.statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.success))
        binding.txtStatusDot.text = getString(R.string.status_active_short)
        binding.txtStatusDot.setTextColor(getColor(R.color.success))
        binding.fabToggle.text = getString(R.string.action_pause)
        binding.fabToggle.setIconResource(R.drawable.ic_shield_on)
        binding.fabToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.primary))
        startShieldPulse()
    }

    private fun startShieldPulse() {
        if (shieldPulseSet?.isRunning == true) return
        val scaleX = ObjectAnimator.ofFloat(binding.imgShield, "scaleX", 1f, 1.1f, 1f).apply {
            duration = 1800
            repeatCount = ObjectAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(binding.imgShield, "scaleY", 1f, 1.1f, 1f).apply {
            duration = 1800
            repeatCount = ObjectAnimator.INFINITE
        }
        val glowAlpha = ObjectAnimator.ofFloat(binding.shieldGlow, "alpha", 0.4f, 0.9f, 0.4f).apply {
            duration = 1800
            repeatCount = ObjectAnimator.INFINITE
        }
        shieldPulseSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY, glowAlpha)
            start()
        }
    }

    private fun stopShieldPulse() {
        shieldPulseSet?.cancel()
        shieldPulseSet = null
        binding.imgShield.scaleX = 1f
        binding.imgShield.scaleY = 1f
        binding.shieldGlow.alpha = 0.3f
    }

    private fun animateNumber(view: TextView, target: Int) {
        val current = view.text.toString().toIntOrNull() ?: 0
        if (current == target) return
        ObjectAnimator.ofInt(current, target).apply {
            duration = 600
            addUpdateListener { view.text = it.animatedValue.toString() }
            start()
        }
    }

    private fun startForegroundServiceIfNeeded() {
        runCatching { GuardianForegroundService.start(this) }
    }

    private fun checkDeviceAdmin() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, GuardianDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.device_admin_title)
                .setMessage(R.string.device_admin_body)
                .setCancelable(false)
                .setPositiveButton(R.string.device_admin_enable) { _, _ ->
                    runCatching {
                        startActivity(
                            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                                putExtra(
                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    getString(R.string.device_admin_explainer)
                                )
                            }
                        )
                    }
                }
                .setNegativeButton(R.string.device_admin_later, null)
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_dashboard, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> {
                exportCsv()
                true
            }
            R.id.action_clear -> {
                confirmClear()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
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
                    events.forEach { event ->
                        append(event.id).append(',')
                        append(event.packageName).append(',')
                        append(event.reason.name).append(',')
                        append((event.matchedTerm ?: "").replace(",", " ")).append(',')
                        append(event.timestamp).append(',')
                        append(fmt.format(Date(event.timestamp))).append('\n')
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
                    File(dir, name).apply { writeText(csv) }
                }
                Snackbar.make(
                    binding.root,
                    getString(R.string.csv_saved, file.absolutePath),
                    Snackbar.LENGTH_LONG
                ).show()
            } catch (t: Throwable) {
                Timber.e(t)
                Snackbar.make(binding.root, R.string.export_failed, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopShieldPulse()
    }
}
