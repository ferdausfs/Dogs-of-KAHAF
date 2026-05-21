package com.guardian.shield.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityBlockOverlayBinding
import com.guardian.shield.ui.unlock.DelayUnlockActivity

class BlockOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockOverlayBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        val detail = intent.getStringExtra(EXTRA_DETAIL).orEmpty()

        binding.txtPackage.text = pkg

        // Temp block কিনা check করো
        if (detail.startsWith("temp_block:")) {
            val mins = detail.removePrefix("temp_block:").removeSuffix("min").trim()
            binding.txtReason.text = "🚫 $mins মিনিটের জন্য ব্লক করা হয়েছে"
            binding.txtReason.setTextColor(Color.parseColor("#FF4444"))
            // Unlock button temp block এ hide
            binding.btnUnlock.visibility = View.GONE
        } else {
            binding.txtReason.text = formatReason(reason, detail)
            binding.txtReason.setTextColor(Color.parseColor("#FFB300"))
            binding.btnUnlock.visibility = View.VISIBLE
            binding.btnUnlock.setOnClickListener {
                startActivity(Intent(this, DelayUnlockActivity::class.java).apply {
                    putExtra(DelayUnlockActivity.EXTRA_PACKAGE, pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                finish()
            }
        }

        binding.btnHome.setOnClickListener { goHome() }
        vibrate()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })
    }

    private fun goHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
        finish()
    }

    private fun formatReason(reason: String, detail: String): String = when (reason) {
        "AI_DETECTION" -> getString(R.string.overlay_reason_ai)
        "KEYWORD_MATCH" -> getString(R.string.overlay_reason_kw, detail)
        "APP_BLOCKED" -> getString(R.string.overlay_reason_app)
        "SCHEDULE_BLOCKED" -> getString(R.string.overlay_reason_sched)
        else -> getString(R.string.overlay_reason_manual)
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                    ?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Throwable) {}
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_DETAIL = "extra_detail"
    }
}