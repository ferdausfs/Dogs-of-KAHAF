package com.guardian.shield.ui.overlay

import android.content.Intent
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityBlockOverlayBinding
import com.guardian.shield.ui.unlock.DelayUnlockActivity
import dagger.hilt.android.AndroidEntryPoint

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
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        vibrate()

        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: "unknown"
        val term = intent.getStringExtra(EXTRA_TERM)
        binding.tvBlockedPackage.text = pkg
        binding.tvDetail.text = term?.let { "Matched: $it" } ?: getString(R.string.stay_strong)

        binding.btnHome.setOnClickListener {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(home); finish()
        }
        binding.btnRequestUnlock.setOnClickListener {
            startActivity(Intent(this, DelayUnlockActivity::class.java))
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() = runCatching {
        val v = if (android.os.Build.VERSION.SDK_INT >= 31)
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.vibrate(android.os.VibrationEffect.createOneShot(180, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }

    @Suppress("MissingSuperCall")
    override fun onBackPressed() { /* prevent dismissal */ }
}
