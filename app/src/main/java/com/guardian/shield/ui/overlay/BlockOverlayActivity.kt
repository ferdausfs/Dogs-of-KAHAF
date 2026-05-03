package com.guardian.shield.ui.overlay

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityBlockOverlayBinding
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.ui.unlock.DelayUnlockActivity
import dagger.hilt.android.AndroidEntryPoint

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* swallow — user must use PIN */ }
        })

        populateContent()
        setupButtons()
    }

    private fun populateContent() {
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "App"
        val reason  = intent.getStringExtra(EXTRA_REASON)   ?: BlockReason.APP_BLOCKED.name
        val detail  = intent.getStringExtra(EXTRA_DETAIL)   ?: ""

        binding.tvBlockedApp.text  = appName
        binding.tvBlockMessage.text = "Blocked for your protection"

        // FIX: enum valueOf instead of string comparison — safe against renames
        val blockReason = try {
            BlockReason.valueOf(reason)
        } catch (_: Exception) { null }

        binding.tvBlockReason.text = when (blockReason) {
            BlockReason.APP_BLOCKED      -> "📵 This app is on your blocked list"
            BlockReason.KEYWORD_DETECTED -> "🔤 Blocked keyword detected: \"$detail\""
            BlockReason.AI_DETECTED      -> "🤖 Unsafe content detected ($detail)"
            null                         -> "⚠️ Content violation"
        }
    }

    private fun setupButtons() {
        binding.btnUnderstand.setOnClickListener {
            val delaySecs = intent.getIntExtra(EXTRA_DELAY_SECS, 30)
            startActivity(Intent(this, DelayUnlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_PKG,              intent.getStringExtra(EXTRA_PKG) ?: "")
                putExtra(EXTRA_APP_NAME,         intent.getStringExtra(EXTRA_APP_NAME) ?: "")
                // FIX: Use constant instead of hardcoded string
                putExtra(DelayUnlockActivity.EXTRA_DELAY_SECS, delaySecs)
            })
            finish()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // FIX: HOME and APP_SWITCH cannot be intercepted by Activities —
        // removed dead code. Only BACK and MENU can be swallowed here.
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }
    // FIX: onUserLeaveHint() dead code removed
}