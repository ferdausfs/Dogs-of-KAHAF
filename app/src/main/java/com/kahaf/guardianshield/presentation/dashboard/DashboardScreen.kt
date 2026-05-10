package com.kahaf.guardianshield.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kahaf.guardianshield.R
import com.kahaf.guardianshield.domain.model.BlockEvent
import com.kahaf.guardianshield.domain.model.BlockReason
import com.kahaf.guardianshield.presentation.common.GuardianTopBar
import com.kahaf.guardianshield.presentation.common.PinEntryDialog
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
    onOpenDomains: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val settings by vm.appSettings.collectAsStateWithLifecycle()
    val blocks by vm.blocksToday.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val byReason by vm.blocksByReasonToday.collectAsStateWithLifecycle()
    val topApps by vm.topBlockedAppsToday.collectAsStateWithLifecycle()

    // v3.1.2: dropped the unused `rememberCoroutineScope()` — nothing in this
    // composable was launching a coroutine, so the scope was dead weight.
    var showSettingsPin by remember { mutableStateOf(false) }

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

            // v3.0.0: "Today's Activity" — segmented bar by reason + top blocked apps.
            TodaysActivityCard(byReason = byReason, topApps = topApps)

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavTile(
                    label = stringResource(R.string.nav_domains),
                    icon = Icons.Filled.Language,
                    onClick = onOpenDomains,
                    modifier = Modifier.weight(1f)
                )
                NavTile(
                    label = stringResource(R.string.set_title),
                    icon = Icons.Filled.Settings,
                    onClick = {
                        if (settings.settingsPinEnabled && settings.settingsPinHash.isNotBlank()) {
                            showSettingsPin = true
                        } else {
                            onOpenSettings()
                        }
                    },
                    modifier = Modifier.weight(1f)
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

    if (showSettingsPin) {
        PinEntryDialog(
            expectedHash = settings.settingsPinHash,
            onVerified = {
                showSettingsPin = false
                onOpenSettings()
            },
            onDismiss = { showSettingsPin = false }
        )
    }
}

@Composable
private fun TodaysActivityCard(
    byReason: Map<BlockReason, Int>,
    topApps: List<Pair<String, Int>>
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.dash_today_activity),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            ReasonBar(byReason)
            Spacer(Modifier.height(10.dp))
            ReasonLegend(byReason)
            if (topApps.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.dash_top_blocked_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                topApps.forEach { (pkg, count) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(pkg, style = MaterialTheme.typography.bodySmall)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                "  $count  ",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasonBar(byReason: Map<BlockReason, Int>) {
    val total = byReason.values.sum().coerceAtLeast(1)
    val orderedReasons = BlockReason.values().toList()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
    ) {
        var x = 0f
        val w = size.width
        val h = size.height
        if (byReason.isEmpty()) {
            drawRect(Color(0xFFE0E0E0), topLeft = Offset(0f, 0f), size = Size(w, h))
            return@Canvas
        }
        orderedReasons.forEach { reason ->
            val count = byReason[reason] ?: 0
            if (count > 0) {
                val segW = w * count / total.toFloat()
                drawRect(
                    color = colorFor(reason),
                    topLeft = Offset(x, 0f),
                    size = Size(segW, h)
                )
                x += segW
            }
        }
    }
}

@Composable
private fun ReasonLegend(byReason: Map<BlockReason, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BlockReason.values().forEach { r ->
            val count = byReason[r] ?: 0
            if (count > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                    ) { Canvas(Modifier.fillMaxSize()) { drawRect(colorFor(r)) } }
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "${labelFor(r)}: $count",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun colorFor(r: BlockReason): Color = when (r) {
    BlockReason.APP_RULE -> Color(0xFF1976D2)
    BlockReason.KEYWORD -> Color(0xFFEF6C00)
    BlockReason.SCHEDULE -> Color(0xFF6A1B9A)
    BlockReason.AI_NSFW -> Color(0xFFD32F2F)
    BlockReason.AUTO_LOCK -> Color(0xFF2E7D32)
}

private fun labelFor(r: BlockReason): String = when (r) {
    BlockReason.APP_RULE -> "App rule"
    BlockReason.KEYWORD -> "Keyword/domain"
    BlockReason.SCHEDULE -> "Schedule"
    BlockReason.AI_NSFW -> "AI NSFW"
    BlockReason.AUTO_LOCK -> "Auto-lock"
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
