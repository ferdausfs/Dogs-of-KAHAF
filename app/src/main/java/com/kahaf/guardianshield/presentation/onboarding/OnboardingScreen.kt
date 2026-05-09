package com.kahaf.guardianshield.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kahaf.guardianshield.R
import com.kahaf.guardianshield.presentation.common.GuardianTopBar

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { GuardianTopBar(stringResource(R.string.app_name)) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.onb_welcome_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.onb_welcome_body),
                style = MaterialTheme.typography.bodyMedium
            )
            PermissionRow(
                title = stringResource(R.string.onb_perm_accessibility),
                granted = state.accessibility,
                onGrant = { vm.openAccessibility() },
                onRefresh = { vm.refresh() }
            )
            PermissionRow(
                title = stringResource(R.string.onb_perm_overlay),
                granted = state.overlay,
                onGrant = { vm.openOverlay() },
                onRefresh = { vm.refresh() }
            )
            PermissionRow(
                title = stringResource(R.string.onb_perm_notifications),
                granted = state.notifications,
                onGrant = { vm.openNotifications() },
                onRefresh = { vm.refresh() }
            )
            PermissionRow(
                title = stringResource(R.string.onb_perm_battery),
                granted = state.battery,
                onGrant = { vm.openBattery() },
                onRefresh = { vm.refresh() }
            )

            Button(
                enabled = state.canFinish,
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.onb_finish)) }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    onGrant: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (granted) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32)
                    )
                }
                Text(
                    title,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (granted) {
                Text(stringResource(R.string.onb_granted), color = Color(0xFF2E7D32))
            } else {
                Row {
                    OutlinedButton(onClick = onRefresh) { Text("↻") }
                    Button(onClick = onGrant, modifier = Modifier.padding(start = 8.dp)) {
                        Text(stringResource(R.string.onb_grant))
                    }
                }
            }
        }
    }
}
