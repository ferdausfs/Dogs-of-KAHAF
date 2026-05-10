package com.guardian.shield.ui.overlay

import android.content.Intent
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityBlockOverlayBinding
import com.guardian.shield.ui.unlock.DelayUnlockActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * v12 (2.1.2):
 *  • Defensive: ViewBinding inflation can fail on theme conflict — try
 *    block falls back to finish() instead of crashing.
 *  • Vibrator service can be null on some emulators / Android Auto.
 *
 * v11 (2.1.1):
 *  • setShowWhenLocked / setTurnScreenOn wrapped in runCatching.
 */
@AndroidEntryPoint
class BlockOverlayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE = "extra_pkg"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_TERM = "extra_term"
    }

    private var binding: ActivityBlockOverlayBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val b = runCatching { ActivityBlockOverlayBinding.inflate(layoutInflater) }
            .onFailure { Timber.e(it, "Failed to inflate BlockOverlay layout — finishing") }
            .getOrNull()
        if (b == null) { runCatching { finish() }; return }
        binding = b
        setContentView(b.root)

        runCatching { setShowWhenLocked(true) }
        runCatching { setTurnScreenOn(true) }
        vibrate()

        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: "unknown"
        val term = intent.getStringExtra(EXTRA_TERM)
        b.tvBlockedPackage.text = pkg
        b.tvDetail.text = term?.let { "Matched: $it" } ?: getString(R.string.stay_strong)

        b.btnHome.setOnClickListener { goHomeAndFinish() }
        b.btnRequestUnlock.setOnClickListener {
            runCatching { startActivity(Intent(this, DelayUnlockActivity::class.java)) }
                .onFailure { Timber.w(it, "Failed to start DelayUnlockActivity") }
            runCatching { finish() }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHomeAndFinish()
        })
    }

    private fun goHomeAndFinish() {
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(home)
        }.onFailure { Timber.w(it, "goHome failed") }
        runCatching { finish() }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() = runCatching {
        val v: Vibrator? = if (android.os.Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
        v?.vibrate(android.os.VibrationEffect.createOneShot(180, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onDestroy() {
        binding = null
        super.onDestroy()
    }
}
