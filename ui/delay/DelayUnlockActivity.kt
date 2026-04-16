package com.kahaf.guardian.ui.delay

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.kahaf.guardian.R
import com.kahaf.guardian.data.local.prefs.SecurePrefsManager
import com.kahaf.guardian.databinding.ActivityDelayUnlockBinding
import com.kahaf.guardian.service.KahafForegroundService
import com.kahaf.guardian.ui.common.*
import com.kahaf.guardian.ui.pin.PinVerifyActivity
import com.kahaf.guardian.ui.settings.SettingsActivity
import com.kahaf.guardian.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DelayUnlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDelayUnlockBinding

    @Inject
    lateinit var securePrefs: SecurePrefsManager

    private var countDownTimer: CountDownTimer? = null
    private var purpose: String = Constants.PURPOSE_SETTINGS

    private val pinVerifyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == PinVerifyActivity.RESULT_PIN_VERIFIED) {
            onPinVerified()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDelayUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        purpose = intent.getStringExtra(Constants.EXTRA_PURPOSE) ?: Constants.PURPOSE_SETTINGS

        val delaySeconds = securePrefs.getDelaySeconds().toLong()

        startCountdown(delaySeconds)

        binding.btnProceed.setOnClickListener {
            val intent = Intent(this, PinVerifyActivity::class.java)
            pinVerifyLauncher.launch(intent)
        }
    }

    private fun startCountdown(seconds: Long) {
        binding.btnProceed.isEnabled = false
        binding.progressCountdown.max = (seconds * 10).toInt()
        binding.progressCountdown.progress = (seconds * 10).toInt()

        countDownTimer = object : CountDownTimer(seconds * 1000, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                binding.tvCountdown.text = secondsLeft.toString()
                binding.progressCountdown.progress = (millisUntilFinished / 100).toInt()
            }

            override fun onFinish() {
                binding.tvCountdown.text = "0"
                binding.progressCountdown.progress = 0
                binding.btnProceed.isEnabled = true
                binding.btnProceed.text = getString(R.string.proceed)
            }
        }.start()
    }

    private fun onPinVerified() {
        when (purpose) {
            Constants.PURPOSE_SETTINGS -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                finish()
            }
            Constants.PURPOSE_DISABLE -> {
                // Disable protection
                securePrefs.setProtectionActive(false)
                KahafForegroundService.stop(this)
                finish()
            }
            else -> finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}