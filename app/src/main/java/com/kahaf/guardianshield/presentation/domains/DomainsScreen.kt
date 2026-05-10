package com.kahaf.guardianshield.presentation.domains

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.kahaf.guardianshield.presentation.common.GuardianTopBar

@Composable
fun DomainsScreen(
    onBack: () -> Unit,
    vm: DomainsViewModel = hiltViewModel()
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }

    Scaffold(topBar = {
        GuardianTopBar(stringResource(R.string.domains_title), onBack = onBack)
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; vm.clearError() },
                label = { Text(stringResource(R.string.domains_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = error != null,
                supportingText = {
                    error?.let { Text(stringResource(R.string.domains_invalid)) }
                }
            )
            Button(
                onClick = {
                    vm.add(input)
                    if (vm.error.value == null) input = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.kw_add)) }

            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(rule.domain, fontWeight = FontWeight.Medium)
                                Text(
                                    "matches *.${rule.domain}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { vm.delete(rule.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "delete")
                            }
                        }
                    }
                }
            }
        }
    }
}
