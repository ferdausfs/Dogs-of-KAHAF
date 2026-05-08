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
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_FEMALE
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_MALE
import com.guardian.shield.data.local.datastore.GuardianPreferences.Companion.GENDER_NONE
import com.guardian.shield.databinding.ActivitySettingsBinding
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.ui.permissions.PermissionsActivity
import com.guardian.shield.ui.setup.PinVerifyActivity
import com.guardian.shield.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val vm: SettingsViewModel by viewModels()
    @Inject lateinit var pinManager: PinManager

    /** Suppress chip-listener feedback while we sync UI ← state. */
    @Volatile private var bindingGenderFromState = false

    private val pickModel = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { copyModel(it) } }

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

        binding.btnApps.setOnClickListener { startActivity(Intent(this, AppListActivity::class.java)) }
        binding.btnKeywords.setOnClickListener { startActivity(Intent(this, KeywordActivity::class.java)) }
        binding.btnUploadModel.setOnClickListener { pickModel.launch(arrayOf("*/*")) }
        binding.btnPermissionHealth.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        binding.swKeyword.setOnCheckedChangeListener { _, v -> vm.setKeywordFilter(v) }
        binding.swAi.setOnCheckedChangeListener { _, v -> vm.setAiDetection(v) }
        binding.sliderDelay.addOnChangeListener { _, value, _ -> vm.setDelaySeconds(value.toInt()) }
        binding.sliderThreshold.addOnChangeListener { _, value, _ -> vm.setAiThreshold(value) }

        // Gender chips — Material ChipGroup with singleSelection.
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

                    // Sync gender chips without re-triggering the listener.
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

                    binding.tvGenderModelStatus.text = when {
                        s.userGender == GENDER_NONE ->
                            "Opposite-gender filter is OFF (gender not set)."
                        !s.genderModelAvailable ->
                            "⚠️ gender_model.tflite missing — falling back to standard NSFW only."
                        else ->
                            "Active: blocking ${if (s.userGender == GENDER_MALE) "female" else "male"} NSFW content."
                    }
                }
            }
        }
    }

    private fun copyModel(uri: Uri) {
        runCatching {
            val out = File(filesDir, AiDetector.MODEL_FILE)
            contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            vm.refresh()
            binding.tvModelStatus.text = "Model loaded ✓"
        }.onFailure { binding.tvModelStatus.text = "Failed: ${it.message}" }
    }
}
