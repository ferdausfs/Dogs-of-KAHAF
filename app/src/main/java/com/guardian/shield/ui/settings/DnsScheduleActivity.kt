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
            val ok = PrivateDnsController.forceOn(this, host)
            toast(if (ok) getString(R.string.dns_toast_on, host) else getString(R.string.dns_toast_no_perm))
            renderBanner()
        }
        binding.btnDnsTestOff.setOnClickListener {
            val ok = PrivateDnsController.forceRestore(this, PrivateDnsScheduler.cache(this))
            toast(if (ok) getString(R.string.dns_toast_restored) else getString(R.string.dns_toast_no_perm))
            renderBanner()
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
                bindAndSelfGrant()
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
            else -> bindAndSelfGrant()
        }
    }

    /** Bind the shell UserService, then grant ourselves WRITE_SECURE_SETTINGS. */
    private fun bindAndSelfGrant() {
        ShizukuDns.bindService {
            val granted = ShizukuDns.grantSelfSecureSettings(this)
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
            rendering = true
            binding.switchDnsAuto.isChecked = enabled
            binding.inputHost.setText(host)
            rendering = false
            renderTimes()
            renderStatus()
            renderBanner()
        }
    }

    /** Save current UI -> DataStore, mirror to cache, enforce right now. */
    private fun applyNow() {
        val enabled = binding.switchDnsAuto.isChecked
        val host = hostFromUi()
        lifecycleScope.launch {
            prefs.setDnsAuto(enabled, startMin, endMin, host)
            withContext(Dispatchers.IO) {
                PrivateDnsScheduler.syncCache(this@DnsScheduleActivity, enabled, startMin, endMin, host)
                runCatching {
                    val inWindow = PrivateDnsScheduler.isInWindow(
                        PrivateDnsScheduler.nowMinutes(), startMin, endMin
                    )
                    PrivateDnsController.applyDesiredState(
                        this@DnsScheduleActivity, enabled, inWindow, host,
                        PrivateDnsScheduler.cache(this@DnsScheduleActivity)
                    )
                }.onFailure { Timber.e(it, "dns apply failed") }
                PrivateDnsScheduler.reschedule(this@DnsScheduleActivity)
            }
            renderStatus()
        }
    }

    private fun fmt(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

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
            else -> {
                val inWindow = PrivateDnsScheduler.isInWindow(
                    PrivateDnsScheduler.nowMinutes(), startMin, endMin
                )
                if (inWindow) {
                    binding.txtDnsStatus.text = getString(R.string.dns_status_on, host)
                    binding.txtDnsStatusSub.text =
                        getString(R.string.dns_status_on_until, fmt(endMin))
                } else {
                    binding.txtDnsStatus.setText(R.string.dns_status_off_now)
                    binding.txtDnsStatusSub.text =
                        getString(R.string.dns_status_next, fmt(startMin))
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
