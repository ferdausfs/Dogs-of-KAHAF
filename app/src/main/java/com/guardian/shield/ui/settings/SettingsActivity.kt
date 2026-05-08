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

/**
 * FIX-LOG (vs original):
 *  - BUG #6: SettingsActivity had ZERO PIN protection. Anyone could open Settings
 *    and unblock apps / disable AI / clear keywords / reset model. Now we require
 *    a successful PinVerifyActivity result before showing the UI; on cancel we
 *    finish().
 *  - BUG #1 (UX): `tvModelStatus` is now wired to the actual model-loaded state
 *    flowing from the ViewModel, with an explicit warning when AI is ON but no
 *    model has been uploaded — addressing the root cause of "AI does nothing".
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val vm: SettingsViewModel by viewModels()
    @Inject lateinit var pinManager: PinManager

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
        // BUG #6 fix — gate the entire settings screen behind a PIN check.
        binding.root.visibility = View.INVISIBLE
        if (pinManager.isPinSet()) {
            pinVerify.launch(Intent(this, PinVerifyActivity::class.java))
        } else {
            // No PIN set yet → fall through (Main flow forces setup first anyway).
            binding.root.visibility = View.VISIBLE
        }

        binding.btnApps.setOnClickListener { startActivity(Intent(this, AppListActivity::class.java)) }
        binding.btnKeywords.setOnClickListener { startActivity(Intent(this, KeywordActivity::class.java)) }
        binding.btnUploadModel.setOnClickListener { pickModel.launch(arrayOf("*/*")) }
        // v2: open Permission Health from the new Persistence card.
        binding.btnPermissionHealth.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

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
                    binding.tvModelStatus.text = when {
                        s.modelLoaded -> "Model loaded ✓"
                        s.aiDetection -> "⚠️ AI is ON but no model uploaded — detection will NOT work"
                        else          -> "No model uploaded"
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
            // Ask the VM to refresh — file existence is checked there.
            vm.refresh()
            binding.tvModelStatus.text = "Model loaded ✓"
        }.onFailure { binding.tvModelStatus.text = "Failed: ${it.message}" }
    }
}
