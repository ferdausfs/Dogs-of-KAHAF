package com.guardian.shield.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settingsVm: SettingsViewModel by viewModels()
    private val keywordVm: KeywordViewModel   by viewModels()
    private val appListVm: AppListViewModel   by viewModels()

    // FIX: Keep adapter references — avoid unsafe cast
    private lateinit var keywordAdapter: KeywordAdapter
    private lateinit var blockedAdapter: AppRuleAdapter
    private lateinit var whitelistAdapter: AppRuleAdapter

    private var isUpdatingFromState = false

    private val modelPickLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { importModel(it) }
        }

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

    override fun onResume() {
        super.onResume()
        updateModelStatus()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // FIX: Adapter references stored — no unsafe cast needed later
    private fun setupRecyclerViews() {
        keywordAdapter  = KeywordAdapter { id -> keywordVm.removeKeyword(id) }
        blockedAdapter  = AppRuleAdapter { pkg -> appListVm.removeRule(pkg) }
        whitelistAdapter = AppRuleAdapter { pkg -> appListVm.removeRule(pkg) }

        binding.rvKeywords.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter        = keywordAdapter
        }
        binding.rvBlockedApps.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter        = blockedAdapter
        }
        binding.rvWhitelistedApps.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter        = whitelistAdapter
        }
    }

    private fun setupViews() {
        binding.switchKeyword.setOnCheckedChangeListener { _, checked ->
            if (!isUpdatingFromState) settingsVm.toggleKeyword(checked)
        }
        binding.switchAi.setOnCheckedChangeListener { _, checked ->
            if (!isUpdatingFromState) {
                // FIX: ViewModel handles modelAvailable check internally
                settingsVm.toggleAi(checked)
            }
        }
        binding.switchStrictMode.setOnCheckedChangeListener { _, checked ->
            if (!isUpdatingFromState) settingsVm.toggleStrictMode(checked)
        }
        binding.sliderDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val secs = value.toInt()
                settingsVm.setDelaySeconds(secs)
                binding.tvDelayValue.text = "${secs}s"
            }
        }
        binding.sliderAiThreshold.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                settingsVm.setAiThreshold(value)
                binding.tvAiThresholdValue.text = "${(value * 100).toInt()}%"
            }
        }
        binding.btnUploadModel.setOnClickListener {
            modelPickLauncher.launch("*/*")
        }
        binding.btnAddKeyword.setOnClickListener { keywordVm.addKeyword() }
        binding.etKeywordInput.setOnEditorActionListener { _, _, _ ->
            keywordVm.addKeyword(); true
        }
        binding.etKeywordInput.doAfterTextChanged { text ->
            keywordVm.updateInput(text.toString())
        }
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
        val already = if (isWhitelist)
            state.whitelistedApps.map { it.packageName }.toSet()
        else
            state.blockedApps.map { it.packageName }.toSet()

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
                isUpdatingFromState = true
                binding.switchKeyword.isChecked    = state.isKeywordEnabled
                binding.switchAi.isChecked         = state.isAiEnabled
                binding.switchStrictMode.isChecked = state.isStrictMode
                binding.sliderDelay.value          = state.delayUnlockSeconds.toFloat()
                binding.tvDelayValue.text          = "${state.delayUnlockSeconds}s"
                binding.sliderAiThreshold.value    = state.aiThreshold
                binding.tvAiThresholdValue.text    = "${(state.aiThreshold * 100).toInt()}%"
                binding.layoutAiOptions.isVisible  = state.isAiEnabled
                isUpdatingFromState = false

                state.snackMessage?.let { msg ->
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    settingsVm.clearMessage()
                }
            }
        }
    }

    // FIX: updateModelStatus runs on IO thread — no file I/O on Main thread
    private fun updateModelStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val modelAvail = AiDetector.isModelAvailable(this@SettingsActivity)
            val sizeKB     = if (modelAvail)
                AiDetector.modelFile(this@SettingsActivity).length() / 1024
            else 0L

            withContext(Dispatchers.Main) {
                if (modelAvail) {
                    binding.tvModelStatus.text = "✓ Model loaded (${sizeKB}KB)"
                    binding.tvModelStatus.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            this@SettingsActivity, android.R.color.holo_green_dark
                        )
                    )
                } else {
                    binding.tvModelStatus.text = "⚠️ No model — upload .tflite file"
                    binding.tvModelStatus.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            this@SettingsActivity, android.R.color.holo_orange_dark
                        )
                    )
                }
            }
        }
    }

    private fun observeKeywords() {
        lifecycleScope.launch {
            keywordVm.uiState.collectLatest { state ->
                binding.tvKeywordError.isVisible = state.errorMessage != null
                binding.tvKeywordError.text      = state.errorMessage ?: ""
                // FIX: Direct adapter reference — no unsafe cast
                keywordAdapter.submitList(state.keywords)
            }
        }
    }

    private fun observeAppLists() {
        lifecycleScope.launch {
            appListVm.uiState.collectLatest { state ->
                blockedAdapter.submitList(state.blockedApps)
                whitelistAdapter.submitList(state.whitelistedApps)
            }
        }
    }

    private fun importModel(uri: Uri) {
        Timber.d("Importing model from: $uri")
        lifecycleScope.launch {
            try {
                val dest = AiDetector.modelFile(this@SettingsActivity)

                withContext(Dispatchers.IO) {
                    // FIX: Check file size before copying — prevent storage overflow
                    val maxBytes = 100L * 1024 * 1024 // 100MB limit
                    contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        if (pfd.statSize > maxBytes) {
                            throw Exception("Model file too large (max 100MB)")
                        }
                    }

                    // FIX: Copy to temp file first — preserve existing model on failure
                    val temp = File(dest.parent, "model_temp.tflite")
                    try {
                        contentResolver.openInputStream(uri)?.use { input ->
                            temp.outputStream().use { output ->
                                input.copyTo(output, bufferSize = 8192)
                            }
                        } ?: throw Exception("Could not open input stream")

                        // Validate temp file
                        if (!temp.exists() || temp.length() < 1024) {
                            temp.delete()
                            throw Exception("Invalid model file (too small)")
                        }

                        // All good — replace existing model
                        temp.renameTo(dest)
                        Timber.d("Model saved: ${dest.length() / 1024}KB")

                    } catch (e: Exception) {
                        temp.delete() // cleanup temp on failure
                        throw e
                    }
                }

                val sizeKB = dest.length() / 1024
                updateModelStatus()

                val aiEnabled = settingsVm.uiState.value.isAiEnabled
                if (aiEnabled) {
                    sendBroadcast(
                        Intent(GuardianAccessibilityService.ACTION_RELOAD_MODEL).apply {
                            setPackage(packageName)
                        }
                    )
                    settingsVm.showMessage("✓ Model imported (${sizeKB}KB) — AI reloading...")
                } else {
                    settingsVm.showMessage(
                        "✓ Model imported (${sizeKB}KB) — Enable AI Detection to activate"
                    )
                }

            } catch (e: Exception) {
                Timber.e(e, "Model import FAILED")
                settingsVm.showMessage("❌ Import failed: ${e.message}")
            }
        }
    }
}