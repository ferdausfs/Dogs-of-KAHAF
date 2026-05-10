package com.kahaf.guardianshield.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kahaf.guardianshield.R
import com.kahaf.guardianshield.data.PinManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PIN_LEN = 4
private const val MAX_ATTEMPTS = 3
private const val COOLDOWN_SECONDS = 30

/**
 * Verifies an existing PIN before sensitive navigation. Calls [onVerified]
 * exactly once on a correct PIN. Wrong PIN triggers a horizontal shake
 * animation; after [MAX_ATTEMPTS] consecutive failures the keypad is locked
 * out for [COOLDOWN_SECONDS] seconds with a live countdown.
 *
 * v3.0.0
 */
@Composable
fun PinEntryDialog(
    expectedHash: String,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    var entered by remember { mutableStateOf("") }
    var attemptsLeft by remember { mutableIntStateOf(MAX_ATTEMPTS) }
    var cooldown by remember { mutableIntStateOf(0) }
    var showWrong by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            while (cooldown > 0) {
                delay(1000)
                cooldown -= 1
            }
            attemptsLeft = MAX_ATTEMPTS
            showWrong = false
        }
    }

    fun submit() {
        if (entered.length != PIN_LEN || cooldown > 0) return
        if (PinManager.verify(entered, expectedHash)) {
            entered = ""
            onVerified()
        } else {
            showWrong = true
            attemptsLeft -= 1
            entered = ""
            scope.launch {
                shake.snapTo(0f)
                shake.animateTo(-18f, tween(60))
                shake.animateTo(18f, tween(60))
                shake.animateTo(-12f, tween(60))
                shake.animateTo(12f, tween(60))
                shake.animateTo(0f, tween(60))
            }
            if (attemptsLeft <= 0) {
                cooldown = COOLDOWN_SECONDS
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_enter)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.set_theme_system).let { "Cancel" }) }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shake.value },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PinDots(entered.length)
                Spacer(Modifier.height(8.dp))
                if (cooldown > 0) {
                    Text(
                        stringResource(R.string.pin_locked, cooldown),
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (showWrong) {
                    Text(
                        stringResource(R.string.pin_wrong, attemptsLeft),
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(" ")
                }
                Spacer(Modifier.height(8.dp))
                NumericKeypad(
                    enabled = cooldown == 0,
                    onDigit = { d ->
                        if (entered.length < PIN_LEN) entered += d
                        if (entered.length == PIN_LEN) submit()
                    },
                    onBackspace = {
                        if (entered.isNotEmpty()) entered = entered.dropLast(1)
                    },
                    onConfirm = { submit() }
                )
            }
        }
    )
}

/**
 * Two-step PIN setup: enter new PIN, then confirm. Calls [onPinSet] with the
 * raw PIN string on success — the caller is responsible for hashing/storing.
 *
 * v3.0.0
 */
@Composable
fun PinSetupDialog(
    onPinSet: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var stage by remember { mutableIntStateOf(0) }   // 0 = first, 1 = confirm
    var mismatch by remember { mutableStateOf(false) }

    val title = if (stage == 0) R.string.pin_set_new else R.string.pin_confirm
    val current = if (stage == 0) first else second

    fun reset() {
        first = ""; second = ""; stage = 0; mismatch = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PinDots(current.length)
                Spacer(Modifier.height(8.dp))
                if (mismatch) {
                    Text(
                        stringResource(R.string.pin_mismatch),
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(" ")
                }
                Spacer(Modifier.height(8.dp))
                NumericKeypad(
                    enabled = true,
                    onDigit = { d ->
                        if (stage == 0) {
                            if (first.length < PIN_LEN) first += d
                            if (first.length == PIN_LEN) {
                                stage = 1
                                mismatch = false
                            }
                        } else {
                            if (second.length < PIN_LEN) second += d
                            if (second.length == PIN_LEN) {
                                if (first == second && PinManager.isValidFormat(first)) {
                                    onPinSet(first)
                                } else {
                                    reset()
                                }
                            }
                        }
                    },
                    onBackspace = {
                        if (stage == 0 && first.isNotEmpty()) first = first.dropLast(1)
                        else if (stage == 1 && second.isNotEmpty()) second = second.dropLast(1)
                    },
                    onConfirm = { /* auto-submitted on 4th digit */ }
                )
            }
        }
    )
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(PIN_LEN) { i ->
            val on = i < filled
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun NumericKeypad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d ->
                    KeypadButton(d, enabled = enabled) { onDigit(d) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadIconButton(enabled = enabled, onClick = onBackspace) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
            }
            KeypadButton("0", enabled = enabled) { onDigit("0") }
            KeypadIconButton(enabled = enabled, onClick = onConfirm) {
                Icon(Icons.Filled.Check, contentDescription = "Confirm")
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(56.dp)
            .pointerInput(enabled, label) {
                if (enabled) awaitClick { onClick() }
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun KeypadIconButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(56.dp)
            .pointerInput(enabled) {
                if (enabled) awaitClick { onClick() }
            }
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.awaitClick(
    onClick: () -> Unit
) {
    while (true) {
        awaitPointerEventScope {
            val down = awaitPointerEvent()
            if (down.changes.any { it.pressed }) {
                // wait for release
                while (true) {
                    val ev = awaitPointerEvent()
                    if (ev.changes.all { !it.pressed }) {
                        onClick()
                        break
                    }
                }
            }
        }
    }
}
