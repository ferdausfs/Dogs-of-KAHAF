package com.guardian.shield.ui.guard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityAccessibilityPromptBinding
import com.guardian.shield.util.ScreenInsets

class AccessibilityPromptActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccessibilityPromptBinding
    private val handler = Handler(Looper.getMainLooper())

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (isAccessibilityEnabled()) {
                finish()
            } else {
                handler.postDelayed(this, 1_000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        binding = ActivityAccessibilityPromptBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ScreenInsets.padTopForStatusBar(binding.root)

        binding.btnEnable.setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        // Back press → home (not dismiss)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                runCatching {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        handler.post(checkRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(checkRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabled.contains(packageName)
        } catch (_: Throwable) { false }
    }
}
