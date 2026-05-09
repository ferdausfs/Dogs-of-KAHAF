package com.kahaf.guardianshield.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.kahaf.guardianshield.domain.model.BlockEvent
import com.kahaf.guardianshield.presentation.common.GuardianTopBar
import com.kahaf.guardianshield.presentation.common.StatCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    onOpenApps: () -> Unit,
    onOpenKeywords: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val blocks by vm.blocksToday.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        GuardianTopBar(stringResource(R.string.app_name))
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            stringResource(R.string.dash_protection),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (settings.protectionEnabled) "ON" else "OFF",
                            color = if (settings.protectionEnabled)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = settings.protectionEnabled,
                        onCheckedChange = { vm.setProtection(it) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    title = stringResource(R.string.dash_today_blocks),
                    value = "$blocks",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = stringResource(R.string.dash_today_scans),
                    value = "${recent.size}",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavTile(
                    label = stringResource(R.string.apps_title),
                    icon = Icons.Filled.Apps,
                    onClick = onOpenApps,
                    modifier = Modifier.weight(1f)
                )
                NavTile(
                    label = stringResource(R.string.kw_title),
                    icon = Icons.Filled.FilterList,
                    onClick = onOpenKeywords,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavTile(
                    label = stringResource(R.string.sch_title),
                    icon = Icons.Filled.Schedule,
                    onClick = onOpenSchedules,
                    modifier = Modifier.weight(1f)
                )
                NavTile(
                    label = stringResource(R.string.ai_title),
                    icon = Icons.Filled.Build,
                    onClick = onOpenAi,
                    modifier = Modifier.weight(1f)
                )
            }
            Row {
                NavTile(
                    label = stringResource(R.string.set_title),
                    icon = Icons.Filled.Settings,
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                stringResource(R.string.dash_recent_events),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (recent.isEmpty()) {
                Text(stringResource(R.string.dash_no_events))
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(recent, key = { it.id }) { ev -> EventRow(ev) }
                }
            }
        }
    }
}

@Composable
private fun NavTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
        }
    }
}

@Composable
private fun EventRow(event: BlockEvent) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(event.packageName, fontWeight = FontWeight.Medium)
            Text(
                "${event.reason.name} · ${event.detail}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(event.timestamp)),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
