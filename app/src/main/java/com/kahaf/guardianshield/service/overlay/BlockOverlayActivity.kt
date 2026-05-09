package com.kahaf.guardianshield.service.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kahaf.guardianshield.R
import com.kahaf.guardianshield.presentation.theme.GuardianShieldTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

/**
 * Full-screen, no-history Activity launched over offending apps.
 *
 * Activity is registered as `singleInstance + noHistory + excludeFromRecents`
 * so the user can't long-press recents to bypass it.
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

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val reasonRes = intent.getIntExtra(EXTRA_REASON_RES, R.string.blk_reason_app)
        val lockedUntil = intent.getLongExtra(EXTRA_LOCKED_UNTIL, 0L)

        vibrateOnce()
        bindingReady = true

        setContent {
            GuardianShieldTheme {
                BlockOverlayScreen(
                    packageName = pkg,
                    reasonRes = reasonRes,
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

    override fun onBackPressed() {
        // Don't let back close the overlay; redirect to home
        goHome()
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

        fun newIntent(
            context: Context,
            packageName: String,
            reasonRes: Int,
            lockedUntilMs: Long = 0L
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
        }
    }
}

@Composable
private fun BlockOverlayScreen(
    packageName: String,
    reasonRes: Int,
    lockedUntilMs: Long,
    onGoHome: () -> Unit
) {
    var remainingText by remember { mutableStateOf("") }
    LaunchedEffect(lockedUntilMs) {
        if (lockedUntilMs > 0L) {
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
            Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(96.dp)
            )
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
                    text = stringResource(R.string.blk_locked_until, remainingText),
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


