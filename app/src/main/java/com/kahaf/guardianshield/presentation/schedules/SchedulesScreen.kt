package com.kahaf.guardianshield.presentation.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kahaf.guardianshield.R
import com.kahaf.guardianshield.domain.model.DaysMask
import com.kahaf.guardianshield.domain.model.Schedule
import com.kahaf.guardianshield.presentation.common.GuardianTopBar

@Composable
fun SchedulesScreen(
    onBack: () -> Unit,
    vm: SchedulesViewModel = hiltViewModel()
) {
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val apps by vm.allApps.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Schedule?>(null) }

    Scaffold(
        topBar = { GuardianTopBar(stringResource(R.string.sch_title), onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = vm.emptySchedule() }) {
                Icon(Icons.Filled.Add, contentDescription = "new")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(schedules, key = { it.id }) { s ->
                ScheduleRow(
                    schedule = s,
                    onEdit = { editing = s },
                    onDelete = { vm.delete(s.id) }
                )
            }
        }
    }

    editing?.let { current ->
        ScheduleEditorDialog(
            initial = current,
            allApps = apps,
            onDismiss = { editing = null },
            onSave = {
                vm.upsert(it)
                editing = null
            }
        )
    }
}

@Composable
private fun ScheduleRow(
    schedule: Schedule,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(schedule.label, fontWeight = FontWeight.Medium)
                Text(
                    "${formatTime(schedule.startMin)} – ${formatTime(schedule.endMin)} · ${schedule.packages.size} apps",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    daysLabel(schedule.daysMask),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "delete") }
        }
    }
}

@Composable
private fun ScheduleEditorDialog(
    initial: Schedule,
    allApps: List<com.kahaf.guardianshield.domain.model.InstalledApp>,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit
) {
    var label by remember { mutableStateOf(initial.label) }
    var startMin by remember { mutableStateOf(initial.startMin) }
    var endMin by remember { mutableStateOf(initial.endMin) }
    var daysMask by remember { mutableStateOf(initial.daysMask) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    val selected = remember { mutableStateOf(initial.packages.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        label = label.ifBlank { "Schedule" },
                        startMin = startMin,
                        endMin = endMin,
                        daysMask = daysMask,
                        enabled = enabled,
                        packages = selected.value.toList()
                    )
                )
            }) { Text(stringResource(R.string.sch_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        title = { Text(stringResource(R.string.sch_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.sch_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sch_enabled))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(stringResource(R.string.sch_days), fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayChip("M", DaysMask.MON, daysMask) { daysMask = daysMask xor DaysMask.MON }
                    DayChip("T", DaysMask.TUE, daysMask) { daysMask = daysMask xor DaysMask.TUE }
                    DayChip("W", DaysMask.WED, daysMask) { daysMask = daysMask xor DaysMask.WED }
                    DayChip("T", DaysMask.THU, daysMask) { daysMask = daysMask xor DaysMask.THU }
                    DayChip("F", DaysMask.FRI, daysMask) { daysMask = daysMask xor DaysMask.FRI }
                    DayChip("S", DaysMask.SAT, daysMask) { daysMask = daysMask xor DaysMask.SAT }
                    DayChip("S", DaysMask.SUN, daysMask) { daysMask = daysMask xor DaysMask.SUN }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sch_start))
                    OutlinedTextField(
                        value = formatTime(startMin),
                        onValueChange = { v -> parseTime(v)?.let { startMin = it } },
                        modifier = Modifier.padding(start = 8.dp),
                        singleLine = true
                    )
                    Text(stringResource(R.string.sch_end), modifier = Modifier.padding(start = 8.dp))
                    OutlinedTextField(
                        value = formatTime(endMin),
                        onValueChange = { v -> parseTime(v)?.let { endMin = it } },
                        modifier = Modifier.padding(start = 8.dp),
                        singleLine = true
                    )
                }
                Text(
                    stringResource(R.string.sch_packages),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                LazyColumn(Modifier.heightIn(max = 220.dp).fillMaxWidth()) {
                    items(allApps, key = { it.packageName }) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = app.packageName in selected.value,
                                onCheckedChange = { checked ->
                                    selected.value = if (checked) {
                                        selected.value + app.packageName
                                    } else {
                                        selected.value - app.packageName
                                    }
                                }
                            )
                            Text(app.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun DayChip(label: String, bit: Int, mask: Int, onToggle: () -> Unit) {
    FilterChip(
        selected = mask and bit != 0,
        onClick = onToggle,
        label = { Text(label) }
    )
}

private fun formatTime(min: Int): String =
    "%02d:%02d".format(min / 60, min % 60)

private fun parseTime(s: String): Int? {
    val parts = s.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

private fun daysLabel(mask: Int): String {
    val names = listOf("M", "T", "W", "T", "F", "S", "S")
    val sb = StringBuilder()
    for (i in 0..6) sb.append(if (mask and (1 shl i) != 0) names[i] else "·")
    return sb.toString()
}
