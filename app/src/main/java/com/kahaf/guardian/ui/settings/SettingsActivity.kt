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
    private val vm: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnSave.setOnClickListener { vm.save(binding.switchKeyword.isChecked, binding.switchAi.isChecked, binding.switchStrict.isChecked, binding.etDelaySeconds.text.toString().toIntOrNull() ?: 30) }
        binding.btnChangePin.setOnClickListener { startActivity(Intent(this, PinSetupActivity::class.java)) }
        collectFlow(vm.uiState) { binding.switchKeyword.isChecked = it.keywordEnabled; binding.switchAi.isChecked = it.aiEnabled; binding.switchStrict.isChecked = it.strictEnabled; binding.etDelaySeconds.setText(it.delaySeconds.toString()) }
        lifecycleScope.launch { vm.saveSuccess.collect { if (it) { toast("Saved!"); finish() } else toast("Failed") } }
    }
}
