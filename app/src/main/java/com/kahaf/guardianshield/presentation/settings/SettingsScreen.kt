package com.kahaf.guardianshield.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.kahaf.guardianshield.BuildConfig
import com.kahaf.guardianshield.R
import com.kahaf.guardianshield.domain.model.ThemeMode
import com.kahaf.guardianshield.presentation.common.GuardianTopBar
import com.kahaf.guardianshield.presentation.common.PinEntryDialog
import com.kahaf.guardianshield.presentation.common.PinSetupDialog

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRequestReflectionDelay: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel()
) {
    val app by vm.app.collectAsStateWithLifecycle()
    val exported by vm.exportedJson.collectAsStateWithLifecycle()
    val importMsg by vm.importMessage.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    var importDialogOpen by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    // PIN dialog state machine:
    //   "setup"            → first-time PIN creation
    //   "verifyForDisable" → require old PIN before disabling protection
    //   "verifyForChange"  → require old PIN before changing
    //   "setupAfterVerify" → set up new PIN after old verified
    var pinDialogMode by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        GuardianTopBar(stringResource(R.string.set_title), onBack = onBack)
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.set_theme), fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = app.themeMode == ThemeMode.SYSTEM,
                            onClick = { vm.setTheme(ThemeMode.SYSTEM) },
                            label = { Text(stringResource(R.string.set_theme_system)) }
                        )
                        FilterChip(
                            selected = app.themeMode == ThemeMode.LIGHT,
                            onClick = { vm.setTheme(ThemeMode.LIGHT) },
                            label = { Text(stringResource(R.string.set_theme_light)) }
                        )
                        FilterChip(
                            selected = app.themeMode == ThemeMode.DARK,
                            onClick = { vm.setTheme(ThemeMode.DARK) },
                            label = { Text(stringResource(R.string.set_theme_dark)) }
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.set_dynamic_color))
                        Switch(
                            checked = app.dynamicColor,
                            onCheckedChange = { vm.setDynamicColor(it) }
                        )
                    }
                }
            }

            // -- v3.1.0 (legacy merge): Uninstall protection (Device Admin) ----
            val deviceAdminActive by vm.deviceAdminActive.collectAsStateWithLifecycle()
            val autoRevokeDisabled by vm.autoRevokeDisabled.collectAsStateWithLifecycle()
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.set_uninstall_protect),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(R.string.set_uninstall_protect_subtitle),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            if (deviceAdminActive)
                                stringResource(R.string.set_uninstall_active)
                            else
                                stringResource(R.string.set_uninstall_inactive),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (deviceAdminActive)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Switch(
                        checked = app.uninstallProtection || deviceAdminActive,
                        onCheckedChange = { vm.setUninstallProtection(it) }
                    )
                }
            }

            // -- v3.1.0 (legacy merge): Disable permission auto-reset (Android 11+)
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.set_disable_auto_revoke),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(R.string.set_disable_auto_revoke_subtitle),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (autoRevokeDisabled) {
                        Text(
                            stringResource(R.string.onb_granted),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        TextButton(onClick = { vm.requestDisableAutoRevoke() }) {
                            Text(stringResource(R.string.onb_grant))
                        }
                    }
                }
            }

            // -- v3.0.0: Protection PIN card -----------------------------------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.pin_title),
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.pin_enable))
                        Switch(
                            checked = app.settingsPinEnabled,
                            onCheckedChange = { wantOn ->
                                if (wantOn) {
                                    pinDialogMode = "setup"
                                } else {
                                    pinDialogMode = "verifyForDisable"
                                }
                            }
                        )
                    }
                    if (app.settingsPinEnabled) {
                        OutlinedButton(
                            onClick = { pinDialogMode = "verifyForChange" },
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Text(stringResource(R.string.pin_change))
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Configuration", fontWeight = FontWeight.Medium)
                    Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { vm.export() }) {
                            Text(stringResource(R.string.set_export))
                        }
                        Button(onClick = { importDialogOpen = true }) {
                            Text(stringResource(R.string.set_import))
                        }
                    }
                    importMsg?.let {
                        Text(it, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.set_about), fontWeight = FontWeight.Medium)
                    Text(
                        "${stringResource(R.string.set_version)}: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
                    )
                    Text(
                        "Privacy: 100% on-device. No network permission.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    exported?.let { json ->
        AlertDialog(
            onDismissRequest = { vm.clearExport() },
            confirmButton = {
                TextButton(onClick = { vm.clearExport() }) { Text(stringResource(R.string.common_close)) }
            },
            title = { Text(stringResource(R.string.set_export)) },
            text = {
                Column {
                    Text(
                        "Copy the JSON below:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = json,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        )
    }

    if (importDialogOpen) {
        AlertDialog(
            onDismissRequest = { importDialogOpen = false; vm.clearImportMessage() },
            confirmButton = {
                TextButton(onClick = {
                    vm.import(importText)
                    importDialogOpen = false
                    importText = ""
                }) { Text(stringResource(R.string.set_import)) }
            },
            dismissButton = {
                TextButton(onClick = { importDialogOpen = false }) { Text(stringResource(R.string.common_cancel)) }
            },
            title = { Text(stringResource(R.string.set_import)) },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text("Paste JSON here") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // ----- PIN dialogs -------------------------------------------------------
    when (pinDialogMode) {
        "setup" -> PinSetupDialog(
            onPinSet = { pin ->
                vm.setPin(pin)
                pinDialogMode = null
            },
            onDismiss = { pinDialogMode = null }
        )
        "verifyForDisable" -> PinEntryDialog(
            expectedHash = app.settingsPinHash,
            onVerified = {
                vm.disablePin()
                pinDialogMode = null
            },
            onDismiss = { pinDialogMode = null }
        )
        "verifyForChange" -> PinEntryDialog(
            expectedHash = app.settingsPinHash,
            onVerified = { pinDialogMode = "setupAfterVerify" },
            onDismiss = { pinDialogMode = null }
        )
        "setupAfterVerify" -> PinSetupDialog(
            onPinSet = { pin ->
                vm.setPin(pin)
                pinDialogMode = null
            },
            onDismiss = { pinDialogMode = null }
        )
    }
}
