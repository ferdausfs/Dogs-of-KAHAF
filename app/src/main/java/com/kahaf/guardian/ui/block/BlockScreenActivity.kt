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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        binding = ActivityBlockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvBlockReason.text = "Reason: ${intent.getStringExtra(Constants.EXTRA_BLOCK_REASON) ?: "Blocked"}"
        binding.btnGoHome.setOnClickListener { goHome() }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }

    @Deprecated("Deprecated") override fun onBackPressed() { goHome() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_APP_SWITCH) { goHome(); true } else super.onKeyDown(keyCode, event)
}
