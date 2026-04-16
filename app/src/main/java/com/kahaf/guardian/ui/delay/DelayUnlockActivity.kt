package com.kahaf.guardian.ui.delay

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.kahaf.guardian.R
import com.kahaf.guardian.data.local.prefs.SecurePrefsManager
import com.kahaf.guardian.domain.repository.SettingsRepository
import kotlinx.coroutines.launch
import com.kahaf.guardian.databinding.ActivityDelayUnlockBinding
import com.kahaf.guardian.service.KahafForegroundService
import com.kahaf.guardian.ui.pin.PinVerifyActivity
import com.kahaf.guardian.ui.settings.SettingsActivity
import com.kahaf.guardian.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DelayUnlockActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDelayUnlockBinding
    @Inject lateinit var prefs: SecurePrefsManager
    @Inject lateinit var settingsRepo: SettingsRepository
    private var timer: CountDownTimer? = null
    private var purpose = Constants.PURPOSE_SETTINGS

    private val pinLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == PinVerifyActivity.RESULT_PIN_VERIFIED) onVerified() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDelayUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        purpose = intent.getStringExtra(Constants.EXTRA_PURPOSE) ?: Constants.PURPOSE_SETTINGS
        val secs = prefs.getDelaySeconds().toLong()
        binding.btnProceed.isEnabled = false
        binding.progressCountdown.max = (secs * 10).toInt(); binding.progressCountdown.progress = (secs * 10).toInt()
        timer = object : CountDownTimer(secs * 1000, 100) {
            override fun onTick(ms: Long) { binding.tvCountdown.text = ((ms / 1000) + 1).toString(); binding.progressCountdown.progress = (ms / 100).toInt() }
            override fun onFinish() { binding.tvCountdown.text = "0"; binding.progressCountdown.progress = 0; binding.btnProceed.isEnabled = true; binding.btnProceed.text = getString(R.string.proceed) }
        }.start()
        binding.btnProceed.setOnClickListener { pinLauncher.launch(Intent(this, PinVerifyActivity::class.java)) }
    }

    private fun onVerified() {
        when (purpose) {
            Constants.PURPOSE_SETTINGS -> { startActivity(Intent(this, SettingsActivity::class.java)); finish() }
            Constants.PURPOSE_DISABLE -> { lifecycleScope.launch { settingsRepo.setProtectionActive(false) }; KahafForegroundService.stop(this); finish() }
            else -> finish()
        }
    }

    override fun onDestroy() { super.onDestroy(); timer?.cancel() }
}
