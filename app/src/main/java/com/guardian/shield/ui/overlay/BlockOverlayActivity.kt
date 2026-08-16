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

        // Temp-block branch (incl. TASK 3 — 24h hard lock formatting)
        if (detail.startsWith("temp_block:")) {
            val raw = detail.removePrefix("temp_block:").removeSuffix("min").trim()
            val mins = raw.toLongOrNull() ?: 0L
            val displayText = formatDuration(mins)
            binding.txtReason.text = "🚫 $displayText এর জন্য ব্লক করা হয়েছে"
            binding.txtReason.setTextColor(Color.parseColor("#FF4444"))
            // Hard lock — no unlock option
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

        // LEARNING MEMORY — only AI blocks can be "false blocks". When the user
        // says a block was a mistake, remember the offending image's colour
        // pattern so it is never blocked again.
        if (reason == "AI_DETECTION") {
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
        if (mins <= 0) return "কিছুক্ষণ"
        // 24 hour special-case
        if (mins >= 24 * 60) {
            val days = mins / (24 * 60)
            val rest = mins % (24 * 60)
            val hours = rest / 60
            val builder = StringBuilder()
            if (days > 0) builder.append("${days * 24 + hours} ঘন্টা")
            else builder.append("${hours} ঘন্টা")
            return builder.toString().trim()
        }
        if (mins >= 60) {
            val hours = mins / 60
            val remaining = mins % 60
            return if (remaining > 0) "${hours} ঘন্টা ${remaining} মিনিট" else "${hours} ঘন্টা"
        }
        return "$mins মিনিট"
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
        "TAMPER_ATTEMPT" -> "⚠️ ট্যাম্পারিং চেষ্টা সনাক্ত করা হয়েছে! কমিটেড লক থাকা অবস্থায় সেটিংস পরিবর্তন করা নিষেধ।"
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
