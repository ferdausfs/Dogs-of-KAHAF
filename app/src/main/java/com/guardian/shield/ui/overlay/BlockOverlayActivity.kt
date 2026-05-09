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
 * v11 (2.1.1) STABILITY PATCH:
 *  • DEFENSIVE: every onCreate side-effect wrapped in runCatching —
 *    this Activity is launched from a BroadcastReceiver / Accessibility
 *    Service context and any exception kills the user-visible block UI.
 */
@AndroidEntryPoint
class BlockOverlayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE = "extra_pkg"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_TERM = "extra_term"
    }

    private lateinit var binding: ActivityBlockOverlayBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        runCatching { setShowWhenLocked(true) }
        runCatching { setTurnScreenOn(true) }
        vibrate()

        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: "unknown"
        val term = intent.getStringExtra(EXTRA_TERM)
        binding.tvBlockedPackage.text = pkg
        binding.tvDetail.text = term?.let { "Matched: $it" } ?: getString(R.string.stay_strong)

        binding.btnHome.setOnClickListener { goHomeAndFinish() }
        binding.btnRequestUnlock.setOnClickListener {
            runCatching { startActivity(Intent(this, DelayUnlockActivity::class.java)) }
                .onFailure { Timber.w(it, "Failed to start DelayUnlockActivity") }
            finish()
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
        val v = if (android.os.Build.VERSION.SDK_INT >= 31)
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.vibrate(android.os.VibrationEffect.createOneShot(180, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
