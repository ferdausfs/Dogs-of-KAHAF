package com.kahaf.guardianshield.service.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kahaf.guardianshield.R
import com.kahaf.guardianshield.domain.model.BlockReason
import com.kahaf.guardianshield.presentation.theme.GuardianShieldTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen, no-history Activity launched over offending apps.
 *
 * Activity is registered as `singleInstance + noHistory + excludeFromRecents`
 * so the user can't long-press recents to bypass it.
 *
 * v3.0.0 visual improvements:
 *  - Pulsing shield icon (Animatable infinite loop 1f → 1.1f).
 *  - Blocked app icon shown when resolvable from PackageManager.
 *  - Live countdown for AUTO_LOCK and "Blocked until HH:MM" for SCHEDULE.
 *  - Hardware-back gesture is silently swallowed via OnBackPressedDispatcher.
 *  - "I understand" / Go-home button calls finish() (which the no-history
 *    flag plus the launching A11y service's GLOBAL_ACTION_HOME handles).
 */
@AndroidEntryPoint
class BlockOverlayActivity : ComponentActivity() {

    private var bindingReady: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show on lock screen + dismiss keyguard when possible (API 27+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // v3.0.0: swallow back-gesture so a child can't dismiss the overlay.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally a no-op. The user must use the "Go home" button.
            }
        })

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val reasonRes = intent.getIntExtra(EXTRA_REASON_RES, R.string.blk_reason_app)
        val lockedUntil = intent.getLongExtra(EXTRA_LOCKED_UNTIL, 0L)
        val reasonName = intent.getStringExtra(EXTRA_REASON_NAME).orEmpty()

        vibrateOnce()
        bindingReady = true

        setContent {
            GuardianShieldTheme {
                BlockOverlayScreen(
                    packageName = pkg,
                    reasonRes = reasonRes,
                    reasonName = reasonName,
                    lockedUntilMs = lockedUntil,
                    onGoHome = { goHome() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Lifecycle guard — never touch composition state if not ready
        if (!bindingReady) return
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home)
        finish()
    }

    /** Isolated to avoid NewApi lint failures (API 26+ path). */
    private fun vibrateOnce() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vibrateOreo(vm?.defaultVibrator)
            } else {
                @Suppress("DEPRECATION")
                vibrateOreo(getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            }
        } catch (_: Throwable) { /* never throw from overlay */ }
    }

    private fun vibrateOreo(v: Vibrator?) {
        if (v == null || !v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_REASON_RES = "extra_reason_res"
        const val EXTRA_LOCKED_UNTIL = "extra_locked_until"
        const val EXTRA_REASON_NAME = "extra_reason_name"

        fun newIntent(
            context: Context,
            packageName: String,
            reasonRes: Int,
            lockedUntilMs: Long = 0L,
            reasonName: String = ""
        ): Intent = Intent(context, BlockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(EXTRA_PACKAGE, packageName)
            putExtra(EXTRA_REASON_RES, reasonRes)
            putExtra(EXTRA_LOCKED_UNTIL, lockedUntilMs)
            putExtra(EXTRA_REASON_NAME, reasonName)
        }
    }
}

@Composable
private fun BlockOverlayScreen(
    packageName: String,
    reasonRes: Int,
    reasonName: String,
    lockedUntilMs: Long,
    onGoHome: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) { resolveAppIcon(context, packageName) }

    val isAutoLock = reasonName == BlockReason.AUTO_LOCK.name
    val isSchedule = reasonName == BlockReason.SCHEDULE.name

    var remainingText by remember { mutableStateOf("") }
    var scheduleText by remember { mutableStateOf("") }

    // v3.0.0: live countdown for AUTO_LOCK; static "Blocked until HH:MM" for SCHEDULE.
    LaunchedEffect(lockedUntilMs, isAutoLock, isSchedule) {
        if (isAutoLock && lockedUntilMs > 0L) {
            while (true) {
                val rem = lockedUntilMs - System.currentTimeMillis()
                if (rem <= 0) {
                    remainingText = ""
                    break
                }
                val mm = rem / 60000
                val ss = (rem / 1000) % 60
                remainingText = "Locked for %d min %d sec".format(mm, ss)
                delay(1000)
            }
        } else if (isSchedule && lockedUntilMs > 0L) {
            val fmt = SimpleDateFormat("HH:mm", Locale.US)
            scheduleText = "Blocked until ${fmt.format(Date(lockedUntilMs))}"
        } else if (lockedUntilMs > 0L) {
            // Generic countdown (back-compat for callers that don't pass reasonName)
            while (true) {
                val rem = lockedUntilMs - System.currentTimeMillis()
                if (rem <= 0) {
                    remainingText = ""
                    break
                }
                val mm = rem / 60000
                val ss = (rem / 1000) % 60
                remainingText = "%02d:%02d".format(mm, ss)
                delay(1000)
            }
        }
    }

    // Pulse animation for the shield icon (1f → 1.1f → 1f, repeating).
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        pulse.animateTo(
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC0A0E27))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pulsing shield
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulse.value)
            )

            // Blocked app icon (small badge below the shield)
            if (appIcon != null) {
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                ) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = packageName,
                        modifier = Modifier.fillMaxSize().padding(6.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.blk_title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = stringResource(reasonRes),
                color = Color(0xFFE0E0E0),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (packageName.isNotBlank()) {
                Text(
                    text = packageName,
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (remainingText.isNotEmpty()) {
                Text(
                    text = remainingText,
                    color = Color(0xFFFFCC80),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            if (scheduleText.isNotEmpty()) {
                Text(
                    text = scheduleText,
                    color = Color(0xFFFFCC80),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            Button(
                onClick = onGoHome,
                modifier = Modifier.padding(top = 32.dp)
            ) { Text(stringResource(R.string.blk_go_home)) }
        }
    }
}

/** Best-effort resolution of an installed app's launcher icon as ImageBitmap. */
private fun resolveAppIcon(context: Context, pkg: String): ImageBitmap? {
    if (pkg.isBlank()) return null
    return runCatching {
        val drawable: Drawable = context.packageManager.getApplicationIcon(pkg)
        drawable.toImageBitmap()
    }.getOrNull()
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap.asImageBitmap()
    }
    val w = if (intrinsicWidth > 0) intrinsicWidth else 96
    val h = if (intrinsicHeight > 0) intrinsicHeight else 96
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp.asImageBitmap()
}
