package com.guardian.shield.ui.overlay

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityBlockOverlayBinding
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.ui.unlock.DelayUnlockActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen block overlay.
 *
 * Features:
 *  - Back/Home/Recents buttons intercepted (no easy dismiss)
 *  - "I understand" → DelayUnlockActivity (countdown + PIN)
 *  - Auto-finish after brief show to return to home
 */
@AndroidEntryPoint
class BlockOverlayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PKG        = "pkg"
        const val EXTRA_APP_NAME   = "app_name"
        const val EXTRA_REASON     = "reason"
        const val EXTRA_DETAIL     = "detail"
        const val EXTRA_DELAY_SECS = "delay_seconds"
    }

    private lateinit var binding: ActivityBlockOverlayBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on, show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        populateContent()
        setupButtons()
    }

    private fun populateContent() {
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "App"
        val reason  = intent.getStringExtra(EXTRA_REASON) ?: BlockReason.APP_BLOCKED.name
        val detail  = intent.getStringExtra(EXTRA_DETAIL) ?: ""

        binding.tvBlockedApp.text = appName
        binding.tvBlockMessage.text = "Blocked for your protection"

        binding.tvBlockReason.text = when (reason) {
            BlockReason.APP_BLOCKED.name      -> "📵 This app is on your blocked list"
            BlockReason.KEYWORD_DETECTED.name -> "🔤 Blocked keyword detected: \"$detail\""
            BlockReason.AI_DETECTED.name      -> "🤖 Unsafe content detected ($detail)"
            else                              -> "⚠️ Content violation"
        }
    }

    private fun setupButtons() {
        binding.btnUnderstand.setOnClickListener {
            val delaySecs = intent.getIntExtra(EXTRA_DELAY_SECS, 30)
            startActivity(Intent(this, DelayUnlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_PKG,          intent.getStringExtra(EXTRA_PKG) ?: "")
                putExtra(EXTRA_APP_NAME,     intent.getStringExtra(EXTRA_APP_NAME) ?: "")
                putExtra("delay_seconds",    delaySecs)
            })
            finish()
        }
    }

    // Intercept ALL hardware back button attempts — no easy bypass
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH -> true   // Consume — do nothing
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onBackPressed() {
        // Do nothing — overlay cannot be dismissed by back press
    }
}
