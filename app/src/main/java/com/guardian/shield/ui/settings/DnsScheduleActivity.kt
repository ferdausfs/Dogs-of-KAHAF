package com.guardian.shield.ui.settings

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.guardian.shield.R
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.databinding.ActivityDnsScheduleBinding
import com.guardian.shield.service.dns.PrivateDnsController
import com.guardian.shield.service.dns.PrivateDnsScheduler
import com.guardian.shield.service.dns.shizuku.ShizukuDns
import com.guardian.shield.util.ScreenInsets
import dagger.hilt.android.AndroidEntryPoint
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * R5 — Private DNS Auto Mode screen: master switch, DoT hostname (+ safe
 * family presets), daily window pickers (overnight-safe), live status, manual
 * test buttons, and the guided one-time WRITE_SECURE_SETTINGS banner.
 *
 * Editing takes effect IMMEDIATELY (sync cache -> apply desired state ->
 * re-arm boundary alarm) so the user can watch it work; the periodic worker
 * keeps it true afterwards.
 */
@AndroidEntryPoint
class DnsScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDnsScheduleBinding
    @Inject lateinit var prefs: GuardianPreferences

    private var rendering = false
    private var startMin = 20 * 60
    private var endMin = 8 * 60
    // R8 (v3.7.8) — day mask (bit0=Sun..bit6=Sat) + 15-min pause timestamp.
    private var dayMask = GuardianPreferences.DNS_ALL_DAYS
    private var pauseUntilMs = 0L

    private val dayChips by lazy {
        listOf(
            binding.chipDnsSun, binding.chipDnsMon, binding.chipDnsTue,
            binding.chipDnsWed, binding.chipDnsThu, binding.chipDnsFri, binding.chipDnsSat
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDnsScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        ScreenInsets.padTopForStatusBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.switchDnsAuto.setOnCheckedChangeListener { _, isChecked ->
            if (!rendering) applyNow()
        }
        binding.inputHost.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { applyNow(); true } else false
        }
        binding.chipAdguardFamily.setOnClickListener { setHost("family.adguard-dns.com") }
        binding.chipCleanbrowsing.setOnClickListener { setHost("family-filter-dns.cleanbrowsing.org") }
        binding.chipAdguard.setOnClickListener { setHost("dns.adguard-dns.com") }

        binding.btnDnsStart.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                startMin = h * 60 + m; renderTimes(); applyNow()
            }, startMin / 60, startMin % 60, true).show()
        }
        binding.btnDnsEnd.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                endMin = h * 60 + m; renderTimes(); applyNow()
            }, endMin / 60, endMin % 60, true).show()
        }

        // R8 — day chips take effect immediately, just like the times.
        dayChips.forEach { chip ->
            chip.setOnCheckedChangeListener { _, _ -> if (!rendering) applyNow() }
        }

        // R8 — 15-minute pause: restores the user's own DNS right away and
        // resumes automatically (exact alarm at the pause expiry + worker).
        binding.btnDnsPause.setOnClickListener { togglePause() }

        binding.btnDnsCopyCmd.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("adb", PrivateDnsController.ADB_COMMAND))
            Toast.makeText(this, R.string.dns_perm_copied, Toast.LENGTH_LONG).show()
        }
        binding.btnDnsPermRefresh.setOnClickListener { renderBanner() }

        // R6 — one-tap no-computer grant through Shizuku.
        binding.btnDnsShizuku.setOnClickListener { startShizukuFlow() }

        binding.btnDnsTestOn.setOnClickListener {
            val host = hostFromUi()
            if (host.isBlank()) { toast(R.string.dns_status_no_host); return@setOnClickListener }
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    PrivateDnsController.forceOn(this@DnsScheduleActivity, host)
                }
                toast(if (ok) getString(R.string.dns_toast_on, host) else getString(R.string.dns_toast_no_perm))
                renderBanner()
            }
        }
        binding.btnDnsTestOff.setOnClickListener {
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    PrivateDnsController.forceRestore(
                        this@DnsScheduleActivity, PrivateDnsScheduler.cache(this@DnsScheduleActivity)
                    )
                }
                toast(if (ok) getString(R.string.dns_toast_restored) else getString(R.string.dns_toast_no_perm))
                renderBanner()
            }
        }

        loadState()
    }

    override fun onResume() {
        super.onResume()
        renderBanner()
    }

    // ------------------------------------------------------------- Shizuku

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                doSelfGrant()
            } else {
                toast(R.string.dns_toast_no_perm)
            }
            renderBanner()
        }

    override fun onStart() {
        super.onStart()
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }
    }

    override fun onStop() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        super.onStop()
    }

    private fun startShizukuFlow() {
        when {
            !ShizukuDns.isShizukuRunning() -> AlertDialog.Builder(this)
                .setTitle(R.string.dns_perm_title)
                .setMessage(R.string.dns_shizuku_missing)
                .setPositiveButton(R.string.dns_shizuku_open) { _, _ ->
                    runCatching {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"))
                        )
                    }
                }
                .setNegativeButton(R.string.action_later) { _, _ -> }
                .show()
            !ShizukuDns.hasShizukuPermission() -> ShizukuDns.requestPermission()
            else -> doSelfGrant()
        }
    }

    /**
     * R7.2 — exec the shell-side `pm grant` directly via Shizuku.newProcess
     * (no UserService/bind needed) — off the main thread, since it blocks on
     * process execution.
     */
    private fun doSelfGrant() {
        lifecycleScope.launch {
            val granted = withContext(Dispatchers.IO) {
                ShizukuDns.grantSelfSecureSettings(this@DnsScheduleActivity)
            }
            Timber.i("DNS Shizuku self-grant: $granted")
            if (granted) {
                toast(R.string.dns_perm_ok)
                applyNow()
            } else {
                toast(R.string.dns_toast_no_perm)
            }
            renderBanner()
        }
    }

    private fun setHost(host: String) {
        binding.inputHost.setText(host)
        binding.inputHost.setSelection(host.length)
        applyNow()
    }

    private fun hostFromUi(): String =
        binding.inputHost.text?.toString()?.trim()?.lowercase().orEmpty()

    private fun loadState() {
        lifecycleScope.launch {
            val enabled = prefs.dnsAutoEnabled.first()
            startMin = prefs.dnsAutoStartMin.first()
            endMin = prefs.dnsAutoEndMin.first()
            val host = prefs.dnsAutoHost.first()
            dayMask = prefs.dnsAutoDayMask.first()
            pauseUntilMs = prefs.dnsAutoPauseUntilMs.first()
            rendering = true
            binding.switchDnsAuto.isChecked = enabled
            binding.inputHost.setText(host)
            renderDayChips()
            rendering = false
            renderTimes()
            renderPauseButton()
            renderStatus()
            renderBanner()
        }
    }

    /** Read the 7 chips into the bitmask; 0 (all off) coerces back to daily. */
    private fun maskFromChips(): Int {
        var mask = 0
        dayChips.forEachIndexed { i, c -> if (c.isChecked) mask = mask or (1 shl i) }
        return if (mask == 0) GuardianPreferences.DNS_ALL_DAYS else mask
    }

    private fun renderDayChips() {
        dayChips.forEachIndexed { i, c -> c.isChecked = (dayMask and (1 shl i)) != 0 }
    }

    /** R8 — pause/resume toggle; enforces immediately + re-arms the alarm. */
    private fun togglePause() {
        val now = System.currentTimeMillis()
        pauseUntilMs = if (PrivateDnsScheduler.isPaused(pauseUntilMs, now)) 0L
        else now + 15 * 60 * 1000L
        if (pauseUntilMs > 0L) toast(R.string.dns_pause_toast)
        val enabled = binding.switchDnsAuto.isChecked
        val host = hostFromUi()
        lifecycleScope.launch {
            prefs.setDnsAutoPauseUntilMs(pauseUntilMs)
            withContext(Dispatchers.IO) {
                PrivateDnsScheduler.syncCache(
                    this@DnsScheduleActivity, enabled, startMin, endMin, host, dayMask, pauseUntilMs
                )
                runCatching {
                    val effective = PrivateDnsScheduler.isEffectiveNow(
                        PrivateDnsScheduler.nowMinutes(), startMin, endMin, dayMask, pauseUntilMs
                    )
                    PrivateDnsController.applyDesiredState(
                        this@DnsScheduleActivity, enabled, effective, host,
                        PrivateDnsScheduler.cache(this@DnsScheduleActivity)
                    )
                }.onFailure { Timber.e(it, "dns pause apply failed") }
                PrivateDnsScheduler.reschedule(this@DnsScheduleActivity)
            }
            renderPauseButton()
            renderStatus()
        }
    }

    private fun renderPauseButton() {
        binding.btnDnsPause.setText(
            if (PrivateDnsScheduler.isPaused(pauseUntilMs)) R.string.dns_pause_resume
            else R.string.dns_pause_15
        )
    }

    /** Save current UI -> DataStore, mirror to cache, enforce right now. */
    private fun applyNow() {
        val enabled = binding.switchDnsAuto.isChecked
        val host = hostFromUi()
        dayMask = maskFromChips()
        if (dayChips.none { it.isChecked }) {
            rendering = true; renderDayChips(); rendering = false
        }
        lifecycleScope.launch {
            prefs.setDnsAutoAll(enabled, startMin, endMin, host, dayMask)
            withContext(Dispatchers.IO) {
                PrivateDnsScheduler.syncCache(
                    this@DnsScheduleActivity, enabled, startMin, endMin, host, dayMask, pauseUntilMs
                )
                runCatching {
                    val effective = PrivateDnsScheduler.isEffectiveNow(
                        PrivateDnsScheduler.nowMinutes(), startMin, endMin, dayMask, pauseUntilMs
                    )
                    PrivateDnsController.applyDesiredState(
                        this@DnsScheduleActivity, enabled, effective, host,
                        PrivateDnsScheduler.cache(this@DnsScheduleActivity)
                    )
                }.onFailure { Timber.e(it, "dns apply failed") }
                PrivateDnsScheduler.reschedule(this@DnsScheduleActivity)
            }
            renderStatus()
        }
    }

    private fun fmt(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    /** R8 — "HH:MM" for a wall-clock timestamp (pause expiry). */
    private fun fmtTs(ts: Long): String {
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = ts
        return "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }

    private fun renderTimes() {
        binding.btnDnsStart.text = getString(R.string.dns_window_on_at, fmt(startMin))
        binding.btnDnsEnd.text = getString(R.string.dns_window_off_at, fmt(endMin))
    }

    private fun renderStatus() {
        val enabled = binding.switchDnsAuto.isChecked
        val host = hostFromUi()
        binding.txtDnsAutoSub.text = getString(
            if (enabled) R.string.dns_master_on else R.string.dns_master_off
        )
        when {
            !enabled -> {
                binding.txtDnsStatus.setText(R.string.dns_status_disabled)
                binding.txtDnsStatusSub.text = getString(R.string.dns_master_off)
            }
            host.isBlank() -> {
                binding.txtDnsStatus.setText(R.string.dns_status_no_host)
                binding.txtDnsStatusSub.text = getString(R.string.dns_host_hint)
            }
            PrivateDnsScheduler.isPaused(pauseUntilMs) -> {
                // R8 — pause wins over every other state message.
                binding.txtDnsStatus.text = getString(R.string.dns_paused_until, fmtTs(pauseUntilMs))
                binding.txtDnsStatusSub.text = getString(R.string.dns_status_on, host)
            }
            else -> {
                val nowMin = PrivateDnsScheduler.nowMinutes()
                val timeInWindow = PrivateDnsScheduler.isInWindow(nowMin, startMin, endMin)
                val effective = PrivateDnsScheduler.isEffectiveNow(
                    nowMin, startMin, endMin, dayMask, pauseUntilMs
                )
                when {
                    effective -> {
                        binding.txtDnsStatus.text = getString(R.string.dns_status_on, host)
                        binding.txtDnsStatusSub.text =
                            getString(R.string.dns_status_on_until, fmt(endMin))
                    }
                    timeInWindow -> {
                        // Inside the clock window but today isn't selected.
                        binding.txtDnsStatus.setText(R.string.dns_status_off_day)
                        binding.txtDnsStatusSub.text =
                            getString(R.string.dns_status_next, fmt(startMin))
                    }
                    else -> {
                        binding.txtDnsStatus.setText(R.string.dns_status_off_now)
                        binding.txtDnsStatusSub.text =
                            getString(R.string.dns_status_next, fmt(startMin))
                    }
                }
            }
        }
    }

    private fun renderBanner() {
        // Engine probe may exec `su` once (root) — do it off the UI thread.
        lifecycleScope.launch {
            val engine = withContext(Dispatchers.IO) {
                PrivateDnsController.activeEngine(this@DnsScheduleActivity)
            }
            val hasControl = engine != PrivateDnsController.Engine.NONE
            binding.cardDnsPerm.visibility = if (hasControl) View.GONE else View.VISIBLE
            binding.txtDnsEngine.text = getString(
                R.string.dns_engine_label,
                getString(
                    when (engine) {
                        PrivateDnsController.Engine.PERMANENT -> R.string.dns_engine_perm
                        PrivateDnsController.Engine.DEVICE_OWNER -> R.string.dns_engine_owner
                        PrivateDnsController.Engine.ROOT -> R.string.dns_engine_root
                        PrivateDnsController.Engine.SHIZUKU -> R.string.dns_engine_shizuku
                        PrivateDnsController.Engine.NONE -> R.string.dns_engine_none
                    }
                )
            )
            rendering = true
            binding.switchDnsAuto.isEnabled = true
            rendering = false
            renderStatus()
            if (!hasControl && binding.switchDnsAuto.isChecked) {
                binding.txtDnsStatusSub.text = getString(R.string.dns_status_no_perm_sub)
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()
}
