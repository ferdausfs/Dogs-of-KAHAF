package com.guardian.shield.ui.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityReelReminderBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * TASK 2 — Islamic reminder overlay shown after extended reel/short scrolling.
 * Does NOT block the host app; it is a soft full-screen interstitial that the
 * user must acknowledge before returning.
 */
@AndroidEntryPoint
class ReelReminderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReelReminderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        binding = ActivityReelReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Caller package (currently informational only)
        val callerPkg = intent.getStringExtra(EXTRA_CALLER_PKG).orEmpty()

        binding.btnContinue.setOnClickListener { finish() }

        binding.btnOpenQuran.setOnClickListener {
            openFirstAvailableIslamicApp()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    private fun openFirstAvailableIslamicApp() {
        val islamicApps = listOf(
            "com.quran.labs.androidquran",
            "com.greentech.quran",
            "com.salamweb.alquran",
            "com.islamicapp.hadith",
            "com.ais.quran.android"
        )
        for (pkg in islamicApps) {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                try {
                    startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                    return
                } catch (_: Throwable) { /* try next */ }
            }
        }
        // No Islamic app installed → open Play Store search
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=quran+bangla"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/search?q=quran+bangla")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) { /* ignore */ }
        }
        finish()
    }

    companion object {
        const val EXTRA_CALLER_PKG = "extra_caller_pkg"

        fun createIntent(context: Context, callerPkg: String): Intent =
            Intent(context, ReelReminderActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_CALLER_PKG, callerPkg)
            }
    }
}
