package com.kahaf.guardianshield.presentation.aisettings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kahaf.guardianshield.R
import com.kahaf.guardianshield.data.classifier.TfLiteNsfwClassifier
import com.kahaf.guardianshield.presentation.common.GuardianTopBar

/**
 * v3.1.1: surfaces `modelSource` so the user can tell whether the imported
 * model is actually live, or whether the bundled asset / SAFE fallback is
 * in use.
 *
 * v3.0.0: removed the engine FilterChip (stub/real). The detection engine is
 * always the on-device TFLite classifier.
 */
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    vm: AiSettingsViewModel = hiltViewModel()
) {
    val ai by vm.ai.collectAsStateWithLifecycle()
    val apps by vm.allApps.collectAsStateWithLifecycle()
    val modelLoaded by vm.isModelLoaded.collectAsStateWithLifecycle()
    val modelSource by vm.modelSource.collectAsStateWithLifecycle()
    val customImported by vm.customModelImported.collectAsStateWithLifecycle()
    val customSize by vm.customModelSize.collectAsStateWithLifecycle()
    val importStatus by vm.importStatus.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    // v3.1.0 (legacy merge): SAF picker for custom .tflite model import.
    val pickModel = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importCustomNsfwModel(uri)
    }

    Scaffold(topBar = {
        GuardianTopBar(stringResource(R.string.ai_title), onBack = onBack)
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // -- Detection Engine card ----------------------------------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.ai_engine_title),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.ai_engine_subtitle),
                        style = MaterialTheme.typography.bodySmall
                    )
                    val statusText = when {
                        !modelLoaded -> stringResource(R.string.ai_model_status_missing)
                        modelSource == TfLiteNsfwClassifier.ModelSource.CUSTOM_IMPORTED ->
                            stringResource(R.string.ai_model_status_custom)
                        modelSource == TfLiteNsfwClassifier.ModelSource.BUNDLED_ASSET ->
                            stringResource(R.string.ai_model_status_bundled)
                        else -> stringResource(R.string.ai_model_status_loaded)
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (modelLoaded) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    // Min image size
                    Text(
                        stringResource(R.string.ai_min_image_size, ai.minImageSize),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Slider(
                        value = ai.minImageSize.toFloat(),
                        onValueChange = { vm.setMinImageSize(it.toInt()) },
                        valueRange = 50f..500f
                    )
                    // Input normalization
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.ai_input_normalized),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = ai.modelInputNormalized,
                            onCheckedChange = { vm.setModelInputNormalized(it) }
                        )
                    }
                    // Heuristic
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.ai_heuristic),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = ai.heuristicEnabled,
                            onCheckedChange = { vm.setHeuristicEnabled(it) }
                        )
                    }
                }
            }

            // -- Sensitivity (with v3.1.0 LOW/BALANCED/HIGH presets) ------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.ai_sensitivity),
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        Modifier.padding(top = 6.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = ai.sensitivity <= 0.35f,
                            onClick = { vm.applyPreset("LOW") },
                            label = { Text(stringResource(R.string.ai_preset_low)) }
                        )
                        FilterChip(
                            selected = ai.sensitivity in 0.36f..0.64f,
                            onClick = { vm.applyPreset("BALANCED") },
                            label = { Text(stringResource(R.string.ai_preset_balanced)) }
                        )
                        FilterChip(
                            selected = ai.sensitivity > 0.64f,
                            onClick = { vm.applyPreset("HIGH") },
                            label = { Text(stringResource(R.string.ai_preset_high)) }
                        )
                    }
                    Slider(
                        value = ai.sensitivity,
                        onValueChange = { vm.setSensitivity(it) },
                        valueRange = 0f..1f
                    )
                    Text("${(ai.sensitivity * 100).toInt()}%")
                }
            }

            // -- v3.1.0 (legacy merge): Custom NSFW model import ---------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.ai_import_custom_model),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.ai_import_help),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (customImported) {
                        Text(
                            stringResource(R.string.ai_imported_size, customSize ?: "?"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            // SAF: ask the user to pick a .tflite (or any binary).
                            // Some pickers won't show .tflite under a strict mime-type
                            // filter, so we accept "*/*" and validate the magic header
                            // server-side in ModelImportManager.
                            pickModel.launch(arrayOf("*/*"))
                        }) { Text(stringResource(R.string.ai_import_custom_model)) }
                        if (customImported) {
                            OutlinedButton(onClick = { vm.deleteCustomNsfwModel() }) {
                                Text(stringResource(R.string.ai_delete_imported))
                            }
                        }
                    }
                    importStatus?.let { msg ->
                        if (msg.isNotBlank()) {
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }

            // -- Debounce -------------------------------------------------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.ai_debounce),
                        fontWeight = FontWeight.Medium
                    )
                    Text("Frames: ${ai.debounceFrames}")
                    Slider(
                        value = ai.debounceFrames.toFloat(),
                        onValueChange = { vm.setDebounceFrames(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                    Text("Window: ${ai.debounceWindowMs / 1000}s")
                    Slider(
                        value = ai.debounceWindowMs.toFloat(),
                        onValueChange = { vm.setDebounceWindow(it.toLong()) },
                        valueRange = 1000f..15000f
                    )
                }
            }

            // -- Sources --------------------------------------------------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.ai_sources),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyColumn(Modifier.heightIn(max = 320.dp).fillMaxWidth()) {
                        items(apps, key = { it.packageName }) { app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = app.packageName in ai.contentSourcePackages,
                                    onCheckedChange = { vm.toggleSource(app.packageName, it) }
                                )
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(app.label, fontWeight = FontWeight.Medium)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
