package com.kahaf.guardian.ui.block

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.kahaf.guardian.databinding.ActivityBlockScreenBinding
import com.kahaf.guardian.util.Constants
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BlockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make it harder to dismiss
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )

        binding = ActivityBlockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val reason = intent.getStringExtra(Constants.EXTRA_BLOCK_REASON) ?: "Blocked"
        val packageName = intent.getStringExtra(Constants.EXTRA_BLOCKED_PACKAGE) ?: ""

        binding.tvBlockReason.text = "Reason: $reason"

        binding.btnGoHome.setOnClickListener {
            goHome()
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finishAndRemoveTask()
    }

    override fun onBackPressed() {
        goHome()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Block back and recent buttons
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_APP_SWITCH -> {
                goHome()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onPause() {
        super.onPause()
        // If user somehow navigates away, finish this activity
        finishAndRemoveTask()
    }
}