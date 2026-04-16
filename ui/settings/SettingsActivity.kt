package com.kahaf.guardian.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kahaf.guardian.databinding.ActivitySettingsBinding
import com.kahaf.guardian.ui.common.*
import com.kahaf.guardian.ui.pin.PinSetupActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
        observeState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            val delayText = binding.etDelaySeconds.text.toString()
            val delay = delayText.toIntOrNull() ?: 30

            viewModel.saveSettings(
                keywordEnabled = binding.switchKeyword.isChecked,
                aiEnabled = binding.switchAi.isChecked,
                strictEnabled = binding.switchStrict.isChecked,
                delaySeconds = delay
            )
        }

        binding.btnChangePin.setOnClickListener {
            startActivity(Intent(this, PinSetupActivity::class.java))
        }
    }

    private fun observeState() {
        collectFlow(viewModel.uiState) { state ->
            binding.switchKeyword.isChecked = state.keywordDetectionEnabled
            binding.switchAi.isChecked = state.aiDetectionEnabled
            binding.switchStrict.isChecked = state.strictModeEnabled
            binding.etDelaySeconds.setText(state.delaySeconds.toString())
        }

        lifecycleScope.launch {
            viewModel.saveSuccess.collect { success ->
                if (success) {
                    toast("Settings saved!")
                    finish()
                } else {
                    toast("Failed to save settings")
                }
            }
        }
    }
}