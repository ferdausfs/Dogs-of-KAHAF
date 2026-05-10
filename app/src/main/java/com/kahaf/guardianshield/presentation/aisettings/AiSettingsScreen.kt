package com.kahaf.guardianshield.presentation.aisettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import com.kahaf.guardianshield.presentation.common.GuardianTopBar

/**
 * v3.0.0: removed the engine FilterChip (stub/real). The detection engine is
 * always the on-device TFLite classifier. New controls:
 *   • Model status (Loaded / Not found — safe fallback)
 *   • Minimum image size slider
 *   • Input normalized toggle (for models trained on [0,1] floats)
 *   • Secondary heuristic toggle
 */
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    vm: AiSettingsViewModel = hiltViewModel()
) {
    val ai by vm.ai.collectAsStateWithLifecycle()
    val apps by vm.allApps.collectAsStateWithLifecycle()
    val modelLoaded by vm.isModelLoaded.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    Scaffold(topBar = {
        GuardianTopBar(stringResource(R.string.ai_title), onBack = onBack)
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // -- Detection Engine card (replaces old "engine" chips) -------------
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
                    Text(
                        if (modelLoaded) stringResource(R.string.ai_model_status_loaded)
                        else stringResource(R.string.ai_model_status_missing),
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

            // -- Sensitivity ----------------------------------------------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.ai_sensitivity),
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = ai.sensitivity,
                        onValueChange = { vm.setSensitivity(it) },
                        valueRange = 0f..1f
                    )
                    Text("${(ai.sensitivity * 100).toInt()}%")
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
