package com.guardian.shield.ui.unlock

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.databinding.ActivityDelayUnlockBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DelayUnlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDelayUnlockBinding
    private var timer: CountDownTimer? = null

    @Inject lateinit var prefs: GuardianPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDelayUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        binding.txtPackage.text = pkg

        binding.btnCancel.setOnClickListener {
            timer?.cancel()
            goHome()
        }

        lifecycleScope.launch {
            val seconds = prefs.delaySeconds.first().coerceIn(5, 120)
            startCountdown(seconds)
        }
    }

    private fun startCountdown(seconds: Int) {
        val totalMs = seconds * 1000L
        timer = object : CountDownTimer(totalMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.txtCountdown.text = (millisUntilFinished / 1000).toString()
            }
            override fun onFinish() {
                binding.txtCountdown.text = "0"
                finish()
            }
        }.start()
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

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }
}
