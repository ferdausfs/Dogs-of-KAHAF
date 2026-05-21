package com.guardianshield.app.ui.overlay

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import com.guardianshield.app.databinding.ActivityBlockOverlayBinding
import com.guardianshield.app.util.Constants

class BlockOverlayActivity : Activity() {

    private lateinit var b: ActivityBlockOverlayBinding
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(b.root)

        val pkg = intent.getStringExtra(Constants.EXTRA_BLOCK_PACKAGE) ?: "unknown"
        val reason = intent.getStringExtra(Constants.EXTRA_BLOCK_REASON) ?: "blocked"
        val duration = intent.getLongExtra(Constants.EXTRA_BLOCK_DURATION, 0L)
        val strikes = intent.getIntExtra(Constants.EXTRA_STRIKE_COUNT, 0)

        b.txtPackage.text = pkg
        when (reason) {
            Constants.REASON_AI -> {
                b.txtTitle.text = "🚫 ২৪ ঘন্টার জন্য বন্ধ"
                b.txtSubtitle.text = "AI সতর্কতা — Strike 3/3"
                if (duration > 0) startCountdown(duration)
            }
            "ai_warn" -> {
                b.txtTitle.text = "⚠️ AI সতর্কতা: $strikes/3"
                b.txtSubtitle.text = "${3 - strikes} আর — সাবধান হও"
                b.txtCountdown.text = ""
            }
            Constants.REASON_BLOCKLIST -> {
                b.txtTitle.text = "🚫 অ্যাপ ব্লকড"
                b.txtSubtitle.text = "এই অ্যাপটি ব্লকলিস্টে আছে"
            }
            Constants.REASON_SCHEDULE -> {
                b.txtTitle.text = "⏰ সময়সূচি অনুযায়ী বন্ধ"
                b.txtSubtitle.text = "এখন এই অ্যাপ ব্যবহারের সময় নয়"
            }
            else -> {
                b.txtTitle.text = "🚫 Blocked"
                b.txtSubtitle.text = reason
            }
        }

        b.btnHome.setOnClickListener {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(home)
            finish()
        }
    }

    private fun startCountdown(ms: Long) {
        timer = object : CountDownTimer(ms, 1000) {
            override fun onTick(left: Long) {
                val h = (left / 3_600_000L)
                val m = (left / 60_000L) % 60
                val s = (left / 1_000L) % 60
                b.txtCountdown.text = "অবশিষ্ট: %02d:%02d:%02d".format(h, m, s)
            }
            override fun onFinish() { b.txtCountdown.text = "Block শেষ হয়েছে"; finish() }
        }.start()
    }

    override fun onBackPressed() {
        // Force home — don't let user dismiss.
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home)
        finish()
    }

    override fun onDestroy() { timer?.cancel(); super.onDestroy() }
}
