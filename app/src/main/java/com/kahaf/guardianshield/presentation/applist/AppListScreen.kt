package com.kahaf.guardianshield.presentation.applist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kahaf.guardianshield.R
import com.kahaf.guardianshield.domain.model.AppRuleState
import com.kahaf.guardianshield.presentation.common.GuardianTopBar

@Composable
fun AppListScreen(
    onBack: () -> Unit,
    vm: AppListViewModel = hiltViewModel()
) {
    val q by vm.query.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        GuardianTopBar(stringResource(R.string.apps_title), onBack = onBack)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            OutlinedTextField(
                value = q,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.apps_search_hint)) },
                singleLine = true
            )
            LazyColumn(
                Modifier.fillMaxSize().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(rows, key = { it.app.packageName }) { row ->
                    AppRowCard(
                        row = row,
                        onSetState = { newState -> vm.setState(row.app.packageName, newState) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRowCard(
    row: AppRow,
    onSetState: (AppRuleState) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(row.app.label, fontWeight = FontWeight.Medium)
            Text(
                row.app.packageName,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StateChip(
                    label = stringResource(R.string.state_normal),
                    selected = row.state == AppRuleState.NORMAL,
                    onClick = { onSetState(AppRuleState.NORMAL) }
                )
                StateChip(
                    label = stringResource(R.string.state_blocked),
                    selected = row.state == AppRuleState.BLOCKED,
                    onClick = { onSetState(AppRuleState.BLOCKED) }
                )
                StateChip(
                    label = stringResource(R.string.state_whitelisted),
                    selected = row.state == AppRuleState.WHITELISTED,
                    onClick = { onSetState(AppRuleState.WHITELISTED) }
                )
            }
        }
    }
}

@Composable
private fun StateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
