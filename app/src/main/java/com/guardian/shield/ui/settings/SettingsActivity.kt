package com.guardian.shield.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivitySettingsBinding
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.service.detection.TimeLockManager
import com.guardian.shield.ui.permissions.PermissionsActivity
import com.guardian.shield.ui.setup.PinSetupActivity
import com.guardian.shield.ui.setup.PinVerifyActivity
import com.guardian.shield.viewmodel.SettingsEvent
import com.guardian.shield.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()
    private var pendingModelName: String? = null
    private var uiInitialized = false
    private var isLocked = false

    @Inject lateinit var pinManager: PinManager
    @Inject lateinit var timeLockManager: TimeLockManager

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
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

        val editEnabled = !isLocked

        // ✅ Enable/disable all controls based on lock state
        listOf(
            binding.switchKeyword, binding.switchAi,
            binding.sliderGuardianThreshold, binding.sliderNsfwThreshold,
            binding.sliderGenderThreshold, binding.sliderDelay,
            binding.chipMale, binding.chipFemale, binding.chipNone,
            binding.chip15min, binding.chip30min, binding.chip60min,
            binding.chipVote1, binding.chipVote2, binding.chipVote3, binding.chipVote4,
            binding.btnImportLegacy, binding.btnImportNsfw, binding.btnImportGender,
            binding.btnRemoveLegacy, binding.btnRemoveNsfw, binding.btnRemoveGender,
            binding.btnChangePin
        ).forEach { it.isEnabled = editEnabled }

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

            // ✅ NSFW gate threshold
            binding.sliderNsfwThreshold.addOnChangeListener(
                Slider.OnChangeListener { _, value, fromUser ->
                    if (fromUser) {
                        viewModel.setNsfwGateThreshold(value)
                        binding.txtNsfwThresholdValue.text = "%.2f".format(value)
                    }
                }
            )

            // ✅ Gender confidence threshold
            binding.sliderGenderThreshold.addOnChangeListener(
                Slider.OnChangeListener { _, value, fromUser ->
                    if (fromUser) {
                        viewModel.setGenderThreshold(value)
                        binding.txtGenderThresholdValue.text = "%.2f".format(value)
                    }
                }
            )

            // ✅ Grid vote count chips
            binding.chipVote1.setOnClickListener { viewModel.setGridVoteCount(1) }
            binding.chipVote2.setOnClickListener { viewModel.setGridVoteCount(2) }
            binding.chipVote3.setOnClickListener { viewModel.setGridVoteCount(3) }
            binding.chipVote4.setOnClickListener { viewModel.setGridVoteCount(4) }

            // Gender chips
            binding.chipMale.setOnClickListener { viewModel.setUserGender("MALE") }
            binding.chipFemale.setOnClickListener { viewModel.setUserGender("FEMALE") }
            binding.chipNone.setOnClickListener { viewModel.setUserGender("NONE") }

            // Temp block duration chips
            binding.chip15min.setOnClickListener { viewModel.setTempBlockDurationMins(15) }
            binding.chip30min.setOnClickListener { viewModel.setTempBlockDurationMins(30) }
            binding.chip60min.setOnClickListener { viewModel.setTempBlockDurationMins(60) }

            // Model buttons
            binding.btnImportLegacy.setOnClickListener {
                pendingModelName = AiDetector.MODEL_LEGACY
                pickModel.launch(arrayOf("*/*"))
            }
            binding.btnImportNsfw.setOnClickListener {
                pendingModelName = AiDetector.MODEL_NSFW
                pickModel.launch(arrayOf("*/*"))
            }
            binding.btnImportGender.setOnClickListener {
                pendingModelName = AiDetector.MODEL_GENDER
                pickModel.launch(arrayOf("*/*"))
            }
            binding.btnRemoveLegacy.setOnClickListener {
                viewModel.deleteModel(AiDetector.MODEL_LEGACY)
            }
            binding.btnRemoveNsfw.setOnClickListener {
                viewModel.deleteModel(AiDetector.MODEL_NSFW)
            }
            binding.btnRemoveGender.setOnClickListener {
                viewModel.deleteModel(AiDetector.MODEL_GENDER)
            }
            binding.btnChangePin.setOnClickListener {
                startActivity(Intent(this, PinSetupActivity::class.java))
            }
        }

        // Navigation — lock এ ভিতরে যাওয়া যাবে
        binding.btnApps.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
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

        // ✅ AI Thresholds
        binding.sliderGuardianThreshold.value = s.aiThreshold.coerceIn(0.3f, 0.95f)
        binding.sliderNsfwThreshold.value = s.nsfwGateThreshold.coerceIn(0.3f, 0.95f)
        binding.sliderGenderThreshold.value = s.genderThreshold.coerceIn(0.5f, 0.95f)
        binding.txtGuardianThresholdValue.text = "%.2f".format(s.aiThreshold)
        binding.txtNsfwThresholdValue.text = "%.2f".format(s.nsfwGateThreshold)
        binding.txtGenderThresholdValue.text = "%.2f".format(s.genderThreshold)

        // ✅ Grid vote chips
        when (s.gridVoteCount) {
            1 -> binding.chipVote1.isChecked = true
            2 -> binding.chipVote2.isChecked = true
            3 -> binding.chipVote3.isChecked = true
            4 -> binding.chipVote4.isChecked = true
        }

        // Gender
        when (s.userGender) {
            "MALE" -> binding.chipMale.isChecked = true
            "FEMALE" -> binding.chipFemale.isChecked = true
            else -> binding.chipNone.isChecked = true
        }
        binding.txtGenderStatus.text = when (s.userGender) {
            "MALE" -> getString(R.string.gender_status_male)
            "FEMALE" -> getString(R.string.gender_status_female)
            else -> getString(R.string.gender_status_none)
        }

        // Temp block duration
        when (s.tempBlockDurationMins) {
            15 -> binding.chip15min.isChecked = true
            30 -> binding.chip30min.isChecked = true
            60 -> binding.chip60min.isChecked = true
        }

        // Models
        binding.txtLegacyStatus.text = formatStatus(s.legacyModel)
        binding.txtNsfwStatus.text = formatStatus(s.nsfwModel)
        binding.txtGenderModelStatus.text = formatStatus(s.genderModel)
    }

    private fun formatStatus(slot: com.guardian.shield.viewmodel.ModelSlotUi): String =
        if (slot.isImported) "✓ ${slot.readableSize ?: ""}"
        else getString(R.string.model_missing)
}