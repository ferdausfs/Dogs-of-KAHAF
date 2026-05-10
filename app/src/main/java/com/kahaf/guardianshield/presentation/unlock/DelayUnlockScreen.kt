package com.kahaf.guardianshield.presentation.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kahaf.guardianshield.presentation.common.GuardianTopBar
import com.kahaf.guardianshield.util.GuardianConstants
import kotlinx.coroutines.delay

/**
 * Reflection / cool-down screen — ported from the legacy v2.x
 * `DelayUnlockActivity` into the v3.0.0 (kahaf) Compose architecture.
 *
 * The user must wait [totalSeconds] before they can confirm a sensitive
 * action (e.g. disabling protection, removing the PIN, deleting their
 * blocked-app list). Designed as a self-imposed friction layer; it isn't
 * cryptographic and it isn't meant to stop a determined attacker. It IS
 * meant to stop the impulsive user from undoing their own protection.
 */
@Composable
fun DelayUnlockScreen(
    onConfirmed: () -> Unit,
    onCancel: () -> Unit,
    totalSeconds: Int = GuardianConstants.DEFAULT_DELAY_SECONDS,
    title: String = "Reflect before unlocking"
) {
    var remaining by remember { mutableIntStateOf(totalSeconds.coerceAtLeast(1)) }

    LaunchedEffect(totalSeconds) {
        remaining = totalSeconds.coerceAtLeast(1)
        while (remaining > 0) {
            delay(1000L)
            remaining -= 1
        }
    }

    Scaffold(topBar = {
        GuardianTopBar(title = title, onBack = onCancel)
    }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (remaining > 0) "$remaining" else "0",
                    fontWeight = FontWeight.Bold,
                    fontSize = 96.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (remaining > 0)
                        "Reflect for $remaining seconds…"
                    else
                        "You may now resume — but think twice.",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onConfirmed,
                    enabled = remaining == 0
                ) {
                    Text("Continue")
                }
            }
        }
    }
}
