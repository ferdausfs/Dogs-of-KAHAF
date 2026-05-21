package com.guardianshield.app.ui.scroll

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import com.guardianshield.app.databinding.ActivityScrollSuggestionBinding
import com.guardianshield.app.manager.QuranReminders
import com.guardianshield.app.util.Constants

class ScrollSuggestionActivity : Activity() {

    private lateinit var b: ActivityScrollSuggestionBinding
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityScrollSuggestionBinding.inflate(layoutInflater)
        setContentView(b.root)

        val reminder = QuranReminders.random()
        b.txtSource.text = reminder.sourceLabel
        b.txtArabic.text = reminder.arabic
        b.txtTranslation.text = reminder.translation

        b.btnOpenQuran.setOnClickListener { openQuranApp() }

        // Skip countdown
        b.btnSkip.isEnabled = false
        timer = object : CountDownTimer(
            Constants.SCROLL_SKIP_COUNTDOWN_SEC * 1000L, 1000L
        ) {
            override fun onTick(left: Long) {
                val s = (left / 1000L).toInt()
                b.btnSkip.text = "এড়িয়ে যান ($s)"
            }
            override fun onFinish() {
                b.btnSkip.isEnabled = true
                b.btnSkip.text = "এড়িয়ে যান"
            }
        }.start()

        b.btnSkip.setOnClickListener {
            if (!b.btnSkip.isEnabled) return@setOnClickListener
            finish()
        }
    }

    private fun openQuranApp() {
        val pm = packageManager
        for (pkg in Constants.QURAN_PACKAGES) {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
                return
            }
        }
        // None installed — open Play Store search for "Quran".
        val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=Quran"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(store) }.onFailure {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=Quran"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    override fun onBackPressed() {
        // Allow back only after countdown done.
        if (b.btnSkip.isEnabled) super.onBackPressed()
    }

    override fun onDestroy() { timer?.cancel(); super.onDestroy() }
}
