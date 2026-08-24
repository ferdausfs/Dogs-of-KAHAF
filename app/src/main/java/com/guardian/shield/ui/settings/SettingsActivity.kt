package com.guardian.shield.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.R
import com.guardian.shield.accountability.AccountabilityNotifier
import com.guardian.shield.backup.BackupRestoreManager
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.databinding.ActivitySettingsBinding
import com.guardian.shield.databinding.DialogPartnerBinding
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.ConfirmedSensitiveMemory
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.service.detection.TimeLockManager
import com.guardian.shield.ui.permissions.PermissionsActivity
import com.guardian.shield.ui.setup.PinSetupActivity
import com.guardian.shield.ui.setup.PinVerifyActivity
import com.guardian.shield.viewmodel.SettingsEvent
import com.guardian.shield.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import com.guardian.shield.util.ScreenInsets

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()
    private var pendingModelName: String? = null
    private var uiInitialized = false
    private var isLocked = false

    @Inject lateinit var pinManager: PinManager
    @Inject lateinit var timeLockManager: TimeLockManager
    @Inject lateinit var confirmedSensitiveMemory: ConfirmedSensitiveMemory

    // PHASE 2 (v3.5.0) — accountability partner + PHASE 1c recovery code.
    @Inject lateinit var guardianPrefs: GuardianPreferences
    @Inject lateinit var accountabilityNotifier: AccountabilityNotifier

    // PHASE 4a (v3.5.0) — local settings backup/restore.
    @Inject lateinit var backupManager: BackupRestoreManager

    private val pinLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) initUI() else finish()
    }

    private val pickModel = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val name = pendingModelName
        if (uri != null && name != null) viewModel.importModel(uri, name)
        pendingModelName = null
    }

    // PHASE 4a (v3.5.0) — SAF launchers for backup/restore.
    private val backupExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(BackupRestoreManager.SUGGESTED_MIME)
    ) { uri: Uri? -> if (uri != null) runBackupExport(uri) }

    private val backupImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) confirmBackupImport(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        ScreenInsets.padTopForStatusBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        timeLockManager.clearIfExpired()
        isLocked = timeLockManager.isLocked()

        if (pinManager.isPinSet()) {
            pinLauncher.launch(Intent(this, PinVerifyActivity::class.java))
        } else {
            initUI()
        }
    }

    private fun initUI() {
        if (uiInitialized) return
        uiInitialized = true

        // Lock banner
        if (isLocked) {
            binding.lockBanner.visibility = View.VISIBLE
            binding.txtLockRemaining.text = "🔒 ${timeLockManager.getRemainingFormatted()}"
        } else {
            binding.lockBanner.visibility = View.GONE
        }

        // v3.6.0 (Task D) — read-only visibility of the permanent
        // ConfirmedSensitiveMemory store: just the count. The store is
        // deliberately one-way, so there is no browse/remove UI.
        binding.txtConfirmedCount.text = confirmedSensitiveMemory.size().toString()

        val editEnabled = !isLocked

        // ✅ Enable/disable all controls based on lock state
        listOf(
            binding.switchKeyword, binding.switchAi, binding.switchNotifShield,
            binding.sliderGuardianThreshold, binding.sliderDelay,
            binding.chip15min, binding.chip30min, binding.chip60min,
            binding.chipVote1, binding.chipVote2, binding.chipVote3, binding.chipVote4,
            binding.btnImportLegacy,
            binding.btnRemoveLegacy,
            binding.btnChangePin, binding.btnRecoveryInfo,
            binding.btnPartner, binding.btnPartnerSummary,
            binding.btnBackupExport, binding.btnBackupImport
        ).forEach { it.isEnabled = editEnabled }

        // PHASE 1c/2 (v3.5.0) — security & accountability rows (always wired;
        // lock state only greys them out like every other edit control).
        binding.btnRecoveryInfo.setOnClickListener { showRecoveryCodeDialog() }
        binding.btnPartner.setOnClickListener { if (editEnabled) showPartnerDialog() }
        binding.btnPartnerSummary.setOnClickListener { if (editEnabled) sendWeeklySummary() }

        // PHASE 4a (v3.5.0) — backup/restore rows.
        binding.btnBackupExport.setOnClickListener {
            if (!editEnabled) return@setOnClickListener
            val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            backupExportLauncher.launch("guardian_shield_backup_$stamp.json")
        }
        binding.btnBackupImport.setOnClickListener {
            if (!editEnabled) return@setOnClickListener
            // Same broad picker pattern as the model import above — the file
            // itself is validated strictly in BackupRestoreManager.
            backupImportLauncher.launch(arrayOf("*/*"))
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                guardianPrefs.partnerEmail.collect { email ->
                    val name = runCatching { guardianPrefs.partnerName.first() }.getOrDefault("")
                    binding.txtPartnerStatus.text = if (email.isBlank()) {
                        getString(R.string.partner_status_none)
                    } else {
                        getString(R.string.partner_status_set_fmt, name.ifBlank { email }, email)
                    }
                }
            }
        }

        if (editEnabled) {
            // Protection toggles
            binding.switchKeyword.setOnCheckedChangeListener { _, v ->
                viewModel.setKeywordFilter(v)
            }
            binding.switchAi.setOnCheckedChangeListener { _, v ->
                viewModel.setAiDetection(v)
            }

            // Delay slider
            binding.sliderDelay.addOnChangeListener(Slider.OnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    viewModel.setDelaySeconds(value.toInt())
                    binding.txtDelayValue.text = "${value.toInt()}s"
                }
            })

            // ✅ Guardian threshold
            binding.sliderGuardianThreshold.addOnChangeListener(
                Slider.OnChangeListener { _, value, fromUser ->
                    if (fromUser) {
                        viewModel.setAiThreshold(value)
                        binding.txtGuardianThresholdValue.text = "%.2f".format(value)
                    }
                }
            )

            // ✅ Grid vote count chips
            binding.chipVote1.setOnClickListener { viewModel.setGridVoteCount(1) }
            binding.chipVote2.setOnClickListener { viewModel.setGridVoteCount(2) }
            binding.chipVote3.setOnClickListener { viewModel.setGridVoteCount(3) }
            binding.chipVote4.setOnClickListener { viewModel.setGridVoteCount(4) }

            // Temp block duration chips
            binding.chip15min.setOnClickListener { viewModel.setTempBlockDurationMins(15) }
            binding.chip30min.setOnClickListener { viewModel.setTempBlockDurationMins(30) }
            binding.chip60min.setOnClickListener { viewModel.setTempBlockDurationMins(60) }

            // Model buttons
            binding.btnImportLegacy.setOnClickListener {
                pendingModelName = AiDetector.MODEL_LEGACY
                pickModel.launch(arrayOf("*/*"))
            }
            binding.btnRemoveLegacy.setOnClickListener {
                viewModel.deleteModel(AiDetector.MODEL_LEGACY)
            }
            binding.btnChangePin.setOnClickListener {
                startActivity(Intent(this, PinSetupActivity::class.java))
            }
        }

        // Navigation — lock এ ভিতরে যাওয়া যাবে
        binding.btnApps.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }
        // R4 — Smart Filters + Whitelist screens
        binding.btnFilters.setOnClickListener {
            startActivity(Intent(this, FiltersActivity::class.java))
        }
        binding.btnWhitelist.setOnClickListener {
            startActivity(Intent(this, WhitelistActivity::class.java))
        }
        binding.btnKeywords.setOnClickListener {
            startActivity(Intent(this, KeywordActivity::class.java))
        }
        binding.btnSchedule.setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }
        binding.btnPermissions.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        binding.btnCommitmentLock.setOnClickListener {
            startActivity(Intent(this, TimeLockActivity::class.java))
        }
        // TASK B — launch the pending reports (cooling-off queue) viewer.
        binding.btnPendingReports.setOnClickListener {
            startActivity(Intent(this, com.guardian.shield.ui.pending.PendingReportsActivity::class.java))
        }
        // PHASE 4b (v3.5.0) — Help & FAQ (read-only; deliberately NOT gated by
        // the Time-Lock edit list — information must stay reachable while locked).
        binding.btnHelp.setOnClickListener {
            startActivity(Intent(this, com.guardian.shield.ui.help.HelpActivity::class.java))
        }

        // PHASE 4c (v3.5.0) — notification shade shield row. Enabling the
        // switch without system Notification access pops the honest explainer
        // and deep-links to the system grant screen instead of lying "on".
        binding.switchNotifShield.setOnClickListener {
            val target = binding.switchNotifShield.isChecked
            if (target && !com.guardian.shield.service.shield.NotificationShieldService
                    .isAccessGranted(this)
            ) {
                binding.switchNotifShield.isChecked = false
                showNotifShieldDialog()
            } else {
                lifecycleScope.launch { guardianPrefs.setNotifShieldEnabled(target) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                guardianPrefs.notifShieldEnabled.collect { updateNotifShieldRow(it) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is SettingsEvent.ImportSuccess ->
                            snack(getString(R.string.import_success, event.modelName))
                        is SettingsEvent.ImportFailure ->
                            snack(getString(R.string.import_failed, event.message))
                        is SettingsEvent.ModelDeleted ->
                            snack(getString(R.string.model_deleted, event.modelName))
                    }
                }
            }
        }
    }

    private fun snack(text: String) =
        Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).show()

    private fun render(s: com.guardian.shield.viewmodel.SettingsUiState) {
        // Protection
        binding.switchKeyword.isChecked = s.keywordFilter
        binding.switchAi.isChecked = s.aiDetection
        binding.sliderDelay.value = s.delaySeconds.coerceIn(5, 120).toFloat()
        binding.txtDelayValue.text = "${s.delaySeconds}s"

        // AI Threshold
        binding.sliderGuardianThreshold.value = s.aiThreshold.coerceIn(0.3f, 0.95f)
        binding.txtGuardianThresholdValue.text = "%.2f".format(s.aiThreshold)

        // ✅ Grid vote chips
        when (s.gridVoteCount) {
            1 -> binding.chipVote1.isChecked = true
            2 -> binding.chipVote2.isChecked = true
            3 -> binding.chipVote3.isChecked = true
            4 -> binding.chipVote4.isChecked = true
        }

        // Temp block duration
        when (s.tempBlockDurationMins) {
            15 -> binding.chip15min.isChecked = true
            30 -> binding.chip30min.isChecked = true
            60 -> binding.chip60min.isChecked = true
        }

        // Model
        binding.txtLegacyStatus.text = formatStatus(s.legacyModel)
    }

    private fun formatStatus(slot: com.guardian.shield.viewmodel.ModelSlotUi): String =
        if (slot.isImported) "✓ ${slot.readableSize ?: ""}"
        else getString(R.string.model_missing)

    // ---------------------------------------------------------------------
    // PHASE 1c (v3.5.0) — regenerate recovery code (Settings is PIN-gated,
    // so this stays privileged). The OLD code stops working the instant a
    // new one is generated; only the code's hash is stored either way.
    // ---------------------------------------------------------------------
    private fun showRecoveryCodeDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recovery_regen_title)
            .setMessage(R.string.recovery_regen_msg)
            .setPositiveButton(R.string.recovery_regen_yes) { _, _ ->
                val code = pinManager.generateRecoveryCode()
                if (code == null) {
                    snack(getString(R.string.recovery_code_not_set))
                    return@setPositiveButton
                }
                // Show exactly once; not persisted in plaintext anywhere.
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.recovery_reveal_title)
                    .setMessage(getString(R.string.recovery_reveal_body) + "\n\n" + code)
                    .setPositiveButton(R.string.recovery_copy) { _, _ ->
                        runCatching {
                            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(
                                android.content.ClipData.newPlainText("recovery_code", code)
                            )
                            snack(getString(R.string.recovery_code_copied))
                        }
                    }
                    .setNegativeButton(R.string.recovery_continue, null)
                    .setCancelable(false)
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------------
    // PHASE 2 (v3.5.0) — accountability partner editor + weekly summary.
    // ---------------------------------------------------------------------
    private fun showPartnerDialog() {
        val dialogBinding = DialogPartnerBinding.inflate(LayoutInflater.from(this))
        lifecycleScope.launch {
            runCatching {
                dialogBinding.editPartnerName.setText(guardianPrefs.partnerName.first())
                dialogBinding.editPartnerEmail.setText(guardianPrefs.partnerEmail.first())
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.partner_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.editPartnerName.text?.toString().orEmpty()
                val email = dialogBinding.editPartnerEmail.text?.toString().orEmpty()
                if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                    snack(getString(R.string.partner_email_invalid))
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    runCatching {
                        guardianPrefs.setPartner(name, email)
                        snack(
                            getString(
                                if (email.isBlank()) R.string.partner_removed
                                else R.string.partner_saved
                            )
                        )
                    }
                }
            }
            .setNeutralButton(R.string.partner_remove) { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        guardianPrefs.clearPartner()
                        snack(getString(R.string.partner_removed))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * HONEST MECHANISM: the summary leaves the device only through an app the
     * USER picks. "Email" opens their mail app pre-filled to the partner
     * (one tap still needed to send); "Share" opens the Android share sheet.
     * Nothing is sent silently — the app has no backend and holds no mail
     * credentials.
     */
    private fun sendWeeklySummary() {
        lifecycleScope.launch {
            val email = runCatching { guardianPrefs.partnerEmail.first() }.getOrDefault("")
            if (email.isBlank()) {
                snack(getString(R.string.partner_status_none))
                return@launch
            }
            val name = runCatching { guardianPrefs.partnerName.first() }.getOrDefault("")
                .ifBlank { email }
            val (subject, body) = accountabilityNotifier.buildWeeklySummary(name, email)
            MaterialAlertDialogBuilder(this@SettingsActivity)
                .setTitle(R.string.partner_summary_how_title)
                .setMessage(R.string.partner_summary_how_msg)
                .setPositiveButton(R.string.partner_notif_action_email) { _, _ ->
                    runCatching {
                        startActivity(accountabilityNotifier.buildEmailIntent(email, subject, body))
                    }.onFailure {
                        startActivity(Intent.createChooser(
                            accountabilityNotifier.buildShareIntent(subject, body), null))
                    }
                }
                .setNegativeButton(R.string.partner_notif_action_share) { _, _ ->
                    startActivity(Intent.createChooser(
                        accountabilityNotifier.buildShareIntent(subject, body), null))
                }
                .setNeutralButton(R.string.cancel, null)
                .show()
        }
    }

    // ---- PHASE 4c (v3.5.0) — notification shade shield ----

    override fun onResume() {
        super.onResume()
        // Access can change in system settings while we were paused — refresh
        // the status line against the persisted toggle.
        lifecycleScope.launch {
            updateNotifShieldRow(
                runCatching { guardianPrefs.notifShieldEnabled.first() }.getOrDefault(false)
            )
        }
    }

    private fun updateNotifShieldRow(enabled: Boolean) {
        binding.switchNotifShield.isChecked = enabled
        val granted = com.guardian.shield.service.shield.NotificationShieldService
            .isAccessGranted(this)
        binding.txtNotifShieldSub.text = when {
            enabled && granted -> getString(R.string.notif_shield_sub_on)
            enabled -> getString(R.string.notif_shield_sub_need_perm)
            else -> getString(R.string.notif_shield_sub_off)
        }
    }

    private fun showNotifShieldDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notif_shield_perm_title)
            .setMessage(R.string.notif_shield_perm_msg)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.notif_shield_perm_cta) { _, _ ->
                runCatching {
                    startActivity(
                        com.guardian.shield.service.shield.NotificationShieldService
                            .accessSettingsIntent()
                    )
                }
            }
            .show()
    }

    // ---- PHASE 4a (v3.5.0) — local backup/restore runners ----

    private fun runBackupExport(uri: Uri) {
        lifecycleScope.launch {
            val ok = runCatching {
                backupManager.exportTo(uri, com.guardian.shield.BuildConfig.VERSION_NAME)
            }.isSuccess
            if (ok) {
                binding.txtBackupStatus.text = getString(R.string.backup_export_done)
                snack(getString(R.string.backup_export_done))
            } else {
                snack(getString(R.string.backup_export_failed))
            }
        }
    }

    /** Import is state-changing, so confirm first — the message spells out
     *  exactly what is replaced/merged and what is never touched. */
    private fun confirmBackupImport(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_import_confirm_title)
            .setMessage(R.string.backup_import_confirm_msg)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.backup_import_confirm_btn) { _, _ -> runBackupImport(uri) }
            .show()
    }

    private fun runBackupImport(uri: Uri) {
        lifecycleScope.launch {
            val result = runCatching { backupManager.importFrom(uri) }.getOrNull()
            if (result != null) {
                val msg = getString(
                    R.string.backup_import_done_fmt,
                    result.apps, result.keywords, result.schedules
                )
                binding.txtBackupStatus.text = msg
                snack(msg)
            } else {
                snack(getString(R.string.backup_import_invalid))
            }
        }
    }
}
