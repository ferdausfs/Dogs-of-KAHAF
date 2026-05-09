package com.guardian.shield.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_FEMALE
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_MALE
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_NONE
import com.guardian.shield.databinding.ActivitySettingsBinding
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.ui.permissions.PermissionsActivity
import com.guardian.shield.ui.setup.PinVerifyActivity
import com.guardian.shield.util.GuardianConstants
import com.guardian.shield.viewmodel.SettingsEvent
import com.guardian.shield.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * v10 (2.1.0): added Sensitivity preset chips (Low / Balanced / High).
 *
 * v8 BUG-13 preserved: legacy combined-model copy on Dispatchers.IO.
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val vm: SettingsViewModel by viewModels()
    @Inject lateinit var pinManager: PinManager

    @Volatile private var bindingGenderFromState = false
    @Volatile private var bindingSensitivityFromState = false
    @Volatile private var pendingModelName: String? = null

    private val pickLegacyModel = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { copyLegacyModel(it) } }

    private val pickModel = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val name = pendingModelName
        pendingModelName = null
        if (uri != null && name != null) {
            vm.importModel(uri, name)
        }
    }

    private val pinVerify = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) {
            binding.root.visibility = View.VISIBLE
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.visibility = View.INVISIBLE
        if (pinManager.isPinSet()) {
            pinVerify.launch(Intent(this, PinVerifyActivity::class.java))
        } else {
            binding.root.visibility = View.VISIBLE
        }

        binding.btnApps.setOnClickListener {
            runCatching { startActivity(Intent(this, AppListActivity::class.java)) }
        }
        binding.btnKeywords.setOnClickListener {
            runCatching { startActivity(Intent(this, KeywordActivity::class.java)) }
        }
        binding.btnSchedule.setOnClickListener {
            runCatching { startActivity(Intent(this, ScheduleActivity::class.java)) }
        }
        binding.btnUploadModel.setOnClickListener { pickLegacyModel.launch(arrayOf("*/*")) }
        binding.btnPermissionHealth.setOnClickListener {
            runCatching { startActivity(Intent(this, PermissionsActivity::class.java)) }
        }

        binding.btnImportNsfwModel.setOnClickListener {
            launchPickerFor(AiDetector.NSFW_MODEL_FILE)
        }
        binding.btnResetNsfwModel.setOnClickListener {
            confirmReset(AiDetector.NSFW_MODEL_FILE, "NSFW model")
        }
        binding.btnImportGenderModel.setOnClickListener {
            launchPickerFor(AiDetector.GENDER_MODEL_FILE)
        }
        binding.btnResetGenderModel.setOnClickListener {
            confirmReset(AiDetector.GENDER_MODEL_FILE, "Gender model")
        }

        binding.swKeyword.setOnCheckedChangeListener { _, v -> vm.setKeywordFilter(v) }
        binding.swAi.setOnCheckedChangeListener { _, v -> vm.setAiDetection(v) }
        binding.sliderDelay.addOnChangeListener { _, value, _ -> vm.setDelaySeconds(value.toInt()) }
        binding.sliderThreshold.addOnChangeListener { _, value, _ -> vm.setAiThreshold(value) }

        binding.chipGroupGender.setOnCheckedStateChangeListener { _, checkedIds ->
            if (bindingGenderFromState) return@setOnCheckedStateChangeListener
            val gender = when (checkedIds.firstOrNull()) {
                binding.chipGenderMale.id   -> GENDER_MALE
                binding.chipGenderFemale.id -> GENDER_FEMALE
                binding.chipGenderNone.id   -> GENDER_NONE
                else                        -> GENDER_NONE
            }
            vm.setUserGender(gender)
        }

        // v10: sensitivity preset chips.
        binding.chipGroupSensitivity.setOnCheckedStateChangeListener { _, checkedIds ->
            if (bindingSensitivityFromState) return@setOnCheckedStateChangeListener
            val level = when (checkedIds.firstOrNull()) {
                binding.chipSensLow.id      -> GuardianConstants.SENSITIVITY_LOW
                binding.chipSensHigh.id     -> GuardianConstants.SENSITIVITY_HIGH
                else                        -> GuardianConstants.SENSITIVITY_BALANCED
            }
            vm.setSensitivity(level)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.ui.collect { s ->
                    binding.swKeyword.isChecked = s.keywordFilter
                    binding.swAi.isChecked = s.aiDetection
                    binding.sliderDelay.value = s.delaySeconds.toFloat().coerceIn(5f, 120f)
                    binding.sliderThreshold.value = s.aiThreshold.coerceIn(0.1f, 0.95f)
                    binding.tvModelStatus.text = when {
                        s.modelLoaded -> "Model loaded ✓"
                        s.aiDetection -> "⚠️ AI is ON but no model uploaded — detection will NOT work"
                        else          -> "No model uploaded"
                    }

                    bindingGenderFromState = true
                    try {
                        val targetId = when (s.userGender) {
                            GENDER_MALE   -> binding.chipGenderMale.id
                            GENDER_FEMALE -> binding.chipGenderFemale.id
                            else          -> binding.chipGenderNone.id
                        }
                        if (binding.chipGroupGender.checkedChipId != targetId) {
                            binding.chipGroupGender.check(targetId)
                        }
                    } finally {
                        bindingGenderFromState = false
                    }

                    // v10: sync sensitivity chips.
                    bindingSensitivityFromState = true
                    try {
                        val targetId = when (s.sensitivity) {
                            GuardianConstants.SENSITIVITY_LOW  -> binding.chipSensLow.id
                            GuardianConstants.SENSITIVITY_HIGH -> binding.chipSensHigh.id
                            else                               -> binding.chipSensBalanced.id
                        }
                        if (binding.chipGroupSensitivity.checkedChipId != targetId) {
                            binding.chipGroupSensitivity.check(targetId)
                        }
                    } finally {
                        bindingSensitivityFromState = false
                    }

                    binding.tvSensitivityStatus.text = when (s.sensitivity) {
                        GuardianConstants.SENSITIVITY_LOW ->
                            "Low — only obvious explicit content (threshold 0.85)."
                        GuardianConstants.SENSITIVITY_HIGH ->
                            "High — catches more, may have false positives (threshold 0.65)."
                        else ->
                            "Balanced (recommended) — blocks NSFW, ignores hot photos (threshold 0.78)."
                    }

                    binding.tvGenderModelStatus.text = when {
                        s.userGender == GENDER_NONE ->
                            "Opposite-gender filter is OFF (gender not set)."
                        !s.genderModelAvailable ->
                            "⚠️ gender_model.tflite missing — falling back to standard NSFW only."
                        else ->
                            "Active: blocking ${if (s.userGender == GENDER_MALE) "female" else "male"} NSFW content."
                    }

                    renderModelSlot(
                        statusView   = binding.tvNsfwModelStatus,
                        importBtn    = binding.btnImportNsfwModel,
                        resetBtn     = binding.btnResetNsfwModel,
                        slot         = s.nsfwModel,
                        importedLabel = "✓ Ready",
                        missingLabel  = "✗ Not Imported"
                    )
                    renderModelSlot(
                        statusView   = binding.tvGenderModelImportStatus,
                        importBtn    = binding.btnImportGenderModel,
                        resetBtn     = binding.btnResetGenderModel,
                        slot         = s.genderModel,
                        importedLabel = "✓ Ready",
                        missingLabel  = "✗ Not Imported"
                    )
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.events.collect { evt ->
                    when (evt) {
                        is SettingsEvent.ImportSuccess ->
                            toast("${prettyName(evt.modelName)} imported ✓")
                        is SettingsEvent.ImportFailure ->
                            toast("Import failed: ${evt.message}")
                        is SettingsEvent.ModelDeleted ->
                            toast("${prettyName(evt.modelName)} removed")
                    }
                }
            }
        }
    }

    private fun renderModelSlot(
        statusView: android.widget.TextView,
        importBtn: com.google.android.material.button.MaterialButton,
        resetBtn: com.google.android.material.button.MaterialButton,
        slot: com.guardian.shield.viewmodel.ModelSlotUi,
        importedLabel: String,
        missingLabel: String
    ) {
        val color = if (slot.isImported)
            getColor(android.R.color.holo_green_light)
        else
            getColor(android.R.color.holo_red_light)
        statusView.setTextColor(color)

        statusView.text = when {
            slot.isImporting -> "Importing…"
            slot.isImported  -> "$importedLabel  •  ${slot.readableSize ?: "—"}"
            else             -> missingLabel
        }

        val enabled = !slot.isImporting
        importBtn.isEnabled = enabled
        resetBtn.isEnabled = enabled && slot.isImported
        resetBtn.visibility = if (slot.isImported) View.VISIBLE else View.GONE

        importBtn.text = if (slot.isImported) "Re-Import" else "Import"
    }

    private fun launchPickerFor(modelName: String) {
        pendingModelName = modelName
        pickModel.launch(arrayOf("*/*"))
    }

    private fun confirmReset(modelName: String, label: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove $label?")
            .setMessage("This will delete the imported model from this device. You can import it again anytime.")
            .setPositiveButton("Remove") { _, _ -> vm.resetModel(modelName) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun prettyName(modelName: String): String = when (modelName) {
        AiDetector.NSFW_MODEL_FILE   -> "NSFW model"
        AiDetector.GENDER_MODEL_FILE -> "Gender model"
        else                         -> modelName
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun copyLegacyModel(uri: Uri) {
        binding.btnUploadModel.isEnabled = false
        binding.tvModelStatus.text = "Importing…"
        lifecycleScope.launch {
            val outcome: Result<Long> = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(filesDir, AiDetector.MODEL_FILE)
                    val tmp = File(filesDir, "${AiDetector.MODEL_FILE}.tmp")
                    if (tmp.exists()) tmp.delete()

                    val input = contentResolver.openInputStream(uri)
                        ?: throw IOException("Could not open the selected file")

                    var copied = 0L
                    input.use { inp ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(8 * 1024)
                            var read = inp.read(buf)
                            while (read != -1) {
                                out.write(buf, 0, read)
                                copied += read
                                if (copied > 500L * 1024 * 1024) {
                                    throw IOException("File too large (>500 MB)")
                                }
                                read = inp.read(buf)
                            }
                            out.flush()
                            runCatching { out.fd.sync() }
                        }
                    }
                    if (copied < 1024L) {
                        tmp.delete()
                        throw IOException("File too small to be a valid TFLite model")
                    }
                    if (!isValidTfliteFile(tmp)) {
                        tmp.delete()
                        throw IOException("Not a valid TFLite model (header check failed)")
                    }
                    if (target.exists() && !target.delete()) {
                        tmp.delete()
                        throw IOException("Could not replace previous model")
                    }
                    if (!tmp.renameTo(target)) {
                        tmp.delete()
                        throw IOException("Could not finalise model file")
                    }
                    copied
                }
            }

            binding.btnUploadModel.isEnabled = true
            outcome.onSuccess {
                vm.refresh()
                binding.tvModelStatus.text = "Model loaded ✓"
                toast("Legacy model imported ✓")
            }.onFailure {
                Timber.e(it, "Legacy model import failed")
                binding.tvModelStatus.text = "Failed: ${it.message ?: "unknown error"}"
            }
        }
    }

    private fun isValidTfliteFile(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(8)
            val read = input.read(header)
            if (read < 8) return@runCatching false
            header[4] == 'T'.code.toByte() &&
                header[5] == 'F'.code.toByte() &&
                header[6] == 'L'.code.toByte() &&
                header[7] == '3'.code.toByte()
        }
    }.getOrDefault(false)
}
