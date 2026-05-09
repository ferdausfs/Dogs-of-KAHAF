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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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

@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    vm: AiSettingsViewModel = hiltViewModel()
) {
    val ai by vm.ai.collectAsStateWithLifecycle()
    val apps by vm.allApps.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    Scaffold(topBar = {
        GuardianTopBar(stringResource(R.string.ai_title), onBack = onBack)
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.ai_engine), fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = ai.engine == "stub",
                            onClick = { vm.setEngine("stub") },
                            label = { Text(stringResource(R.string.ai_engine_stub)) }
                        )
                        FilterChip(
                            selected = ai.engine == "real",
                            onClick = { vm.setEngine("real") },
                            label = { Text(stringResource(R.string.ai_engine_real)) }
                        )
                    }
                    Text(
                        stringResource(R.string.ai_model_missing),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
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
