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

        // ✅ Lock banner
        if (isLocked) {
            binding.lockBanner.visibility = View.VISIBLE
            binding.txtLockRemaining.text = "🔒 ${timeLockManager.getRemainingFormatted()}"
        } else {
            binding.lockBanner.visibility = View.GONE
        }

        // ✅ Lock থাকলে সব edit disabled
        val editEnabled = !isLocked
        binding.switchKeyword.isEnabled = editEnabled
        binding.switchAi.isEnabled = editEnabled
        binding.sliderThreshold.isEnabled = editEnabled
        binding.sliderDelay.isEnabled = editEnabled
        binding.chipMale.isEnabled = editEnabled
        binding.chipFemale.isEnabled = editEnabled
        binding.chipNone.isEnabled = editEnabled
        binding.chip15min.isEnabled = editEnabled
        binding.chip30min.isEnabled = editEnabled
        binding.chip60min.isEnabled = editEnabled
        binding.btnImportLegacy.isEnabled = editEnabled
        binding.btnImportNsfw.isEnabled = editEnabled
        binding.btnImportGender.isEnabled = editEnabled
        binding.btnRemoveLegacy.isEnabled = editEnabled
        binding.btnRemoveNsfw.isEnabled = editEnabled
        binding.btnRemoveGender.isEnabled = editEnabled
        binding.btnChangePin.isEnabled = editEnabled

        if (editEnabled) {
            binding.switchKeyword.setOnCheckedChangeListener { _, v -> viewModel.setKeywordFilter(v) }
            binding.switchAi.setOnCheckedChangeListener { _, v -> viewModel.setAiDetection(v) }
            binding.sliderThreshold.addOnChangeListener(Slider.OnChangeListener { _, value, fromUser ->
                if (fromUser) viewModel.setAiThreshold(value)
            })
            binding.sliderDelay.addOnChangeListener(Slider.OnChangeListener { _, value, fromUser ->
                if (fromUser) viewModel.setDelaySeconds(value.toInt())
            })
            binding.chipMale.setOnClickListener { viewModel.setUserGender("MALE") }
            binding.chipFemale.setOnClickListener { viewModel.setUserGender("FEMALE") }
            binding.chipNone.setOnClickListener { viewModel.setUserGender("NONE") }
            binding.chip15min.setOnClickListener { viewModel.setTempBlockDurationMins(15) }
            binding.chip30min.setOnClickListener { viewModel.setTempBlockDurationMins(30) }
            binding.chip60min.setOnClickListener { viewModel.setTempBlockDurationMins(60) }
            binding.btnImportLegacy.setOnClickListener { pendingModelName = AiDetector.MODEL_LEGACY; pickModel.launch(arrayOf("*/*")) }
            binding.btnImportNsfw.setOnClickListener { pendingModelName = AiDetector.MODEL_NSFW; pickModel.launch(arrayOf("*/*")) }
            binding.btnImportGender.setOnClickListener { pendingModelName = AiDetector.MODEL_GENDER; pickModel.launch(arrayOf("*/*")) }
            binding.btnRemoveLegacy.setOnClickListener { viewModel.deleteModel(AiDetector.MODEL_LEGACY) }
            binding.btnRemoveNsfw.setOnClickListener { viewModel.deleteModel(AiDetector.MODEL_NSFW) }
            binding.btnRemoveGender.setOnClickListener { viewModel.deleteModel(AiDetector.MODEL_GENDER) }
            binding.btnChangePin.setOnClickListener { startActivity(Intent(this, PinSetupActivity::class.java)) }
        }

        binding.btnApps.setOnClickListener { startActivity(Intent(this, AppListActivity::class.java)) }
        binding.btnKeywords.setOnClickListener { startActivity(Intent(this, KeywordActivity::class.java)) }
        binding.btnSchedule.setOnClickListener { startActivity(Intent(this, ScheduleActivity::class.java)) }
        binding.btnPermissions.setOnClickListener { startActivity(Intent(this, PermissionsActivity::class.java)) }
        binding.btnCommitmentLock.setOnClickListener { startActivity(Intent(this, TimeLockActivity::class.java)) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is SettingsEvent.ImportSuccess -> snack("Imported: ${event.modelName}")
                        is SettingsEvent.ImportFailure -> snack("Failed: ${event.message}")
                        is SettingsEvent.ModelDeleted -> snack("Removed: ${event.modelName}")
                    }
                }
            }
        }
    }

    private fun snack(text: String) = Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).show()

    private fun render(s: com.guardian.shield.viewmodel.SettingsUiState) {
        binding.switchKeyword.isChecked = s.keywordFilter
        binding.switchAi.isChecked = s.aiDetection
        binding.sliderThreshold.value = s.aiThreshold.coerceIn(0.2f, 0.95f)
        binding.sliderDelay.value = s.delaySeconds.coerceIn(5, 120).toFloat()
        binding.txtThresholdValue.text = "%.2f".format(s.aiThreshold)
        binding.txtDelayValue.text = "${s.delaySeconds}s"
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
        when (s.tempBlockDurationMins) {
            15 -> binding.chip15min.isChecked = true
            30 -> binding.chip30min.isChecked = true
            60 -> binding.chip60min.isChecked = true
        }
        binding.txtLegacyStatus.text = formatStatus(s.legacyModel)
        binding.txtNsfwStatus.text = formatStatus(s.nsfwModel)
        binding.txtGenderModelStatus.text = formatStatus(s.genderModel)
    }

    private fun formatStatus(slot: com.guardian.shield.viewmodel.ModelSlotUi): String =
        if (slot.isImported) "✓ ${slot.readableSize ?: ""}" else getString(R.string.model_missing)
}