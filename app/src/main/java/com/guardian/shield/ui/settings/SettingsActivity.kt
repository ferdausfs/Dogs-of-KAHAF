package com.guardian.shield.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.guardian.shield.databinding.ActivitySettingsBinding
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val vm: SettingsViewModel by viewModels()

    private val pickModel = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { copyModel(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnApps.setOnClickListener { startActivity(Intent(this, AppListActivity::class.java)) }
        binding.btnKeywords.setOnClickListener { startActivity(Intent(this, KeywordActivity::class.java)) }
        binding.btnUploadModel.setOnClickListener { pickModel.launch(arrayOf("*/*")) }

        binding.swKeyword.setOnCheckedChangeListener { _, v -> vm.setKeywordFilter(v) }
        binding.swAi.setOnCheckedChangeListener { _, v -> vm.setAiDetection(v) }
        binding.sliderDelay.addOnChangeListener { _, value, _ -> vm.setDelaySeconds(value.toInt()) }
        binding.sliderThreshold.addOnChangeListener { _, value, _ -> vm.setAiThreshold(value) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.ui.collect { s ->
                    binding.swKeyword.isChecked = s.keywordFilter
                    binding.swAi.isChecked = s.aiDetection
                    binding.sliderDelay.value = s.delaySeconds.toFloat().coerceIn(5f, 120f)
                    binding.sliderThreshold.value = s.aiThreshold.coerceIn(0.1f, 0.95f)
                    binding.tvModelStatus.text = if (s.modelLoaded) "Model loaded ✓" else "No model uploaded"
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
            binding.tvModelStatus.text = "Model loaded ✓"
        }.onFailure { binding.tvModelStatus.text = "Failed: ${it.message}" }
    }
}
