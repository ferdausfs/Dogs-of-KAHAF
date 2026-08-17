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
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityBlockOverlayBinding
import com.guardian.shield.service.detection.FalsePositiveMemory
import com.guardian.shield.ui.unlock.DelayUnlockActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BlockOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockOverlayBinding

    @Inject lateinit var falsePositiveMemory: FalsePositiveMemory

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

        // Temp-block branch (incl. TASK 3 — 24h hard lock formatting).
        // AI-originated temp blocks are tagged "temp_block:NNmin;ai" so we can
        // distinguish them from a plain app-block enforcement (which also uses
        // reason=APP_BLOCKED but has no AI-frame candidate to learn from).
        val isTempBlock = detail.startsWith("temp_block:")
        val isAiBlock = reason == "AI_DETECTION" || detail.endsWith(";ai")
        // Premium redesign — additional optional views, safe to ignore if not in binding
        try {
            binding.txtCategory.text = when (reason) {
                "AI_DETECTION" -> "Adult Content • AI Detection"
                "KEYWORD_MATCH" -> "Keyword • ${detail.take(20)}"
                "APP_BLOCKED" -> if (isAiBlock) "Adult Content • AI Detection" else "App Blocked"
                "SCHEDULE_BLOCKED" -> "Schedule • Outside hours"
                else -> "Blocked Content"
            }
        } catch (_: Throwable) {}

        if (isTempBlock) {
            val raw = detail.removePrefix("temp_block:")
                .substringBefore(";")
                .removeSuffix("min")
                .trim()
            val mins = raw.toLongOrNull() ?: 0L
            val displayText = formatDuration(mins)
            binding.txtReason.text = getString(R.string.overlay_temp_block_fmt, displayText)
            binding.txtReason.setTextColor(Color.parseColor("#FF4444"))
            // Premium: show temp banner card if present
            try {
                binding.cardTempBlock.visibility = View.VISIBLE
                binding.txtTempBanner.text = getString(R.string.overlay_temp_block_fmt, displayText) + " • ${displayText}"
            } catch (_: Throwable) {}
            // Hard lock — no unlock option
            binding.btnUnlock.visibility = View.GONE
        } else {
            binding.txtReason.text = formatReason(reason, detail)
            binding.txtReason.setTextColor(getColor(R.color.warning_amber))
            try {
                binding.cardTempBlock.visibility = View.GONE
            } catch (_: Throwable) {}
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

        // LEARNING MEMORY — only AI blocks can be "false blocks". AI 3rd-strike
        // blocks reach here with reason=APP_BLOCKED but detail tagged ";ai", so
        // gate on isAiBlock rather than the reason string. Non-AI temp blocks
        // (app/schedule enforcement) have no remembered candidate and must not
        // show this button.
        if (isAiBlock) {
            binding.btnMarkFalse.visibility = View.VISIBLE
            binding.btnMarkFalse.setOnClickListener {
                val sig = falsePositiveMemory.takePendingCandidate()
                if (sig != null) {
                    falsePositiveMemory.addSignature(sig)
                    binding.btnMarkFalse.isEnabled = false
                    binding.btnMarkFalse.text = getString(R.string.overlay_mark_false_done)
                    Snackbar.make(binding.root, R.string.overlay_mark_false_done, Snackbar.LENGTH_LONG).show()
                } else {
                    Snackbar.make(binding.root, R.string.overlay_mark_false_unavailable, Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        vibrate()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })
    }

    /**
     * Render a duration (in minutes) in Bangla:
     *   1440 → "২৪ ঘন্টা"
     *   90   → "১ ঘন্টা ৩০ মিনিট"
     *   45   → "৪৫ মিনিট"
     */
    private fun formatDuration(mins: Long): String {
        if (mins <= 0) return getString(R.string.overlay_dur_short)
        if (mins >= 60) {
            val hours = mins / 60
            val remaining = mins % 60
            return if (remaining > 0) getString(R.string.overlay_dur_hours_minutes_fmt, hours, remaining)
            else getString(R.string.overlay_dur_hours_fmt, hours)
        }
        return getString(R.string.overlay_dur_minutes_fmt, mins)
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
        "TAMPER_ATTEMPT" -> getString(R.string.overlay_reason_tamper)
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
