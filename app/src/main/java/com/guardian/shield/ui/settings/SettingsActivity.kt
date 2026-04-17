package com.guardian.shield.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.databinding.ActivitySettingsBinding
import com.guardian.shield.service.detection.AiDetector
import com.guardian.shield.service.accessibility.GuardianAccessibilityService
import com.guardian.shield.viewmodel.SettingsViewModel
import com.guardian.shield.viewmodel.KeywordViewModel
import com.guardian.shield.viewmodel.AppListViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settingsVm: SettingsViewModel by viewModels()
    private val keywordVm: KeywordViewModel by viewModels()
    private val appListVm: AppListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
        setupRecyclerViews()
        setupViews()
        observeSettings()
        observeKeywords()
        observeAppLists()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun setupRecyclerViews() {
        binding.rvKeywords.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = KeywordAdapter { id -> keywordVm.removeKeyword(id) }
        }
        binding.rvBlockedApps.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = AppRuleAdapter { pkg -> appListVm.removeRule(pkg) }
        }
        binding.rvWhitelistedApps.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = AppRuleAdapter { pkg -> appListVm.removeRule(pkg) }
        }
    }

    private fun setupViews() {
        // ── Protection toggles ────────────────────────────────────────
        binding.switchKeyword.setOnCheckedChangeListener { _, checked ->
            settingsVm.toggleKeyword(checked)
        }
        binding.switchAi.setOnCheckedChangeListener { _, checked ->
            settingsVm.toggleAi(checked)
        }

        // ── Delay unlock slider ───────────────────────────────────────
        binding.sliderDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val secs = value.toInt()
                settingsVm.setDelaySeconds(secs)
                binding.tvDelayValue.text = "${secs}s"
            }
        }

        // ── AI threshold slider ───────────────────────────────────────
        binding.sliderAiThreshold.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                settingsVm.setAiThreshold(value)
                binding.tvAiThresholdValue.text = "${(value * 100).toInt()}%"
            }
        }

        // ── Model upload ──────────────────────────────────────────────
        binding.btnUploadModel.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/octet-stream"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, REQ_MODEL_PICK)
        }

        // ── Keyword add ───────────────────────────────────────────────
        binding.btnAddKeyword.setOnClickListener {
            keywordVm.addKeyword()
        }
        binding.etKeywordInput.setOnEditorActionListener { _, _, _ ->
            keywordVm.addKeyword()
            true
        }
        binding.etKeywordInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                keywordVm.updateInput(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // ── App picker buttons ────────────────────────────────────────
        binding.btnAddBlockedApp.setOnClickListener {
            showAppPickerDialog(isWhitelist = false)
        }
        binding.btnAddWhitelistedApp.setOnClickListener {
            showAppPickerDialog(isWhitelist = true)
        }
    }

    private fun showAppPickerDialog(isWhitelist: Boolean) {
        val state = appListVm.uiState.value
        if (state.installedApps.isEmpty()) {
            appListVm.loadInstalledApps()
            Snackbar.make(binding.root, "Loading app list…", Snackbar.LENGTH_SHORT).show()
            return
        }
        val already = if (isWhitelist) state.whitelistedApps.map { it.packageName }.toSet()
                      else state.blockedApps.map { it.packageName }.toSet()
        val available = state.installedApps.filter { it.packageName !in already }
        if (available.isEmpty()) {
            Snackbar.make(binding.root, "No more apps to add", Snackbar.LENGTH_SHORT).show()
            return
        }
        val names = available.map { it.appName }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(if (isWhitelist) "Add Trusted App" else "Block App")
            .setItems(names) { _, idx ->
                val app = available[idx]
                if (isWhitelist) appListVm.addToWhitelist(app)
                else appListVm.addToBlockedList(app)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsVm.uiState.collectLatest { state ->
                binding.switchKeyword.isChecked     = state.isKeywordEnabled
                binding.switchAi.isChecked          = state.isAiEnabled
                binding.sliderDelay.value           = state.delayUnlockSeconds.toFloat()
                binding.tvDelayValue.text           = "${state.delayUnlockSeconds}s"
                binding.sliderAiThreshold.value     = state.aiThreshold
                binding.tvAiThresholdValue.text     = "${(state.aiThreshold * 100).toInt()}%"
                binding.layoutAiOptions.isVisible   = state.isAiEnabled

                val modelAvail = AiDetector.isModelAvailable(this@SettingsActivity)
                binding.tvModelStatus.text = if (modelAvail) "✓ Model loaded" else "No model — upload .tflite"
                binding.tvModelStatus.setTextColor(
                    getColor(if (modelAvail) android.R.color.holo_green_dark else android.R.color.holo_orange_dark)
                )

                state.snackMessage?.let { msg ->
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    settingsVm.clearMessage()
                }
            }
        }
    }

    private fun observeKeywords() {
        lifecycleScope.launch {
            keywordVm.uiState.collectLatest { state ->
                binding.tvKeywordError.isVisible = state.errorMessage != null
                binding.tvKeywordError.text      = state.errorMessage ?: ""
                (binding.rvKeywords.adapter as? KeywordAdapter)?.submitList(state.keywords)
            }
        }
    }

    private fun observeAppLists() {
        lifecycleScope.launch {
            appListVm.uiState.collectLatest { state ->
                (binding.rvBlockedApps.adapter as? AppRuleAdapter)?.submitList(state.blockedApps)
                (binding.rvWhitelistedApps.adapter as? AppRuleAdapter)?.submitList(state.whitelistedApps)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MODEL_PICK && resultCode == RESULT_OK) {
            data?.data?.let { uri -> importModel(uri) }
        }
    }

    private fun importModel(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val dest = AiDetector.modelFile(this@SettingsActivity)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                settingsVm.showMessage("Model imported successfully ✓")
                sendBroadcast(Intent(GuardianAccessibilityService.ACTION_RELOAD_MODEL).apply {
                    setPackage(packageName)
                })
            } catch (e: Exception) {
                settingsVm.showMessage("Import failed: ${e.message}")
            }
        }
    }


    companion object {
        private const val REQ_MODEL_PICK = 201
    }
}

