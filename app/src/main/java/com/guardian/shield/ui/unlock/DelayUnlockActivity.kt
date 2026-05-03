package com.guardian.shield.ui.unlock

import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.guardian.shield.databinding.ActivityDelayUnlockBinding
import com.guardian.shield.viewmodel.PinViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DelayUnlockActivity : AppCompatActivity() {

    companion object {
        // FIX: Constant declared here — referenced by BlockOverlayActivity
        const val EXTRA_DELAY_SECS = "delay_seconds"
    }

    private lateinit var binding: ActivityDelayUnlockBinding
    private val pinViewModel: PinViewModel by viewModels()
    private var countdownTimer: CountDownTimer? = null
    private var delaySecs = 30

    // FIX: Track remaining time for configuration change restore
    private var remainingMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityDelayUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FIX: Always block back press — user MUST enter PIN
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* block — never allow back */ }
        })

        delaySecs = intent.getIntExtra(EXTRA_DELAY_SECS, 30).coerceIn(5, 300)

        // FIX: Restore state on configuration change — prevents double countdown
        if (savedInstanceState != null) {
            remainingMs = savedInstanceState.getLong("remaining_ms", delaySecs * 1000L)
            if (remainingMs > 0) {
                resumeCountdown(remainingMs)
            } else {
                transitionToPin()
            }
        } else {
            startCountdown()
        }

        setupNumpad()
        observePinState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("remaining_ms", remainingMs)
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        super.onDestroy()
    }

    // ── Phase 1: Countdown ─────────────────────────────────────────────

    private fun startCountdown() {
        remainingMs = delaySecs * 1000L
        startTimerFrom(remainingMs)
    }

    private fun resumeCountdown(fromMs: Long) {
        startTimerFrom(fromMs)
    }

    private fun startTimerFrom(durationMs: Long) {
        binding.groupCountdown.isVisible = true
        binding.groupPin.isVisible       = false

        countdownTimer = object : CountDownTimer(durationMs, 1000L) {
            override fun onTick(remaining: Long) {
                remainingMs = remaining
                val secs = (remaining / 1000).toInt() + 1
                binding.tvCountdown.text           = secs.toString()
                binding.progressCountdown.progress =
                    ((secs.toFloat() / delaySecs) * 100).toInt()
            }
            override fun onFinish() {
                remainingMs = 0L
                binding.tvCountdown.text           = "0"
                binding.progressCountdown.progress = 0
                transitionToPin()
            }
        }.start()
    }

    private fun transitionToPin() {
        binding.groupCountdown.isVisible = false
        binding.groupPin.isVisible       = true
    }

    // ── Phase 2: PIN numpad ────────────────────────────────────────────

    private fun setupNumpad() {
        val np = binding.numpad
        mapOf(
            np.btn0 to "0", np.btn1 to "1", np.btn2 to "2",
            np.btn3 to "3", np.btn4 to "4", np.btn5 to "5",
            np.btn6 to "6", np.btn7 to "7", np.btn8 to "8",
            np.btn9 to "9"
        ).forEach { (btn, digit) ->
            btn.setOnClickListener {
                val cur = pinViewModel.uiState.value.input
                if (cur.length < 8) pinViewModel.updateInput(cur + digit)
            }
        }
        np.btnBackspace.setOnClickListener {
            val cur = pinViewModel.uiState.value.input
            if (cur.isNotEmpty()) pinViewModel.updateInput(cur.dropLast(1))
        }
        binding.btnPinConfirm.setOnClickListener { pinViewModel.verifyPin() }
    }

    private fun observePinState() {
        lifecycleScope.launch {
            pinViewModel.uiState.collectLatest { state ->
                // FIX: 8 dots to match MAX_PIN_LENGTH = 8
                updateDots(state.input.length)
                binding.tvPinError.isVisible = state.error != null
                binding.tvPinError.text      = state.error ?: ""
                if (state.error != null) shakePin()
                if (state.isVerified) finishAffinity()
            }
        }
    }

    // FIX: 8 dots — matches PinManager.MAX_PIN_LENGTH = 8
    private fun updateDots(length: Int) {
        listOf(
            binding.dot1, binding.dot2, binding.dot3, binding.dot4,
            binding.dot5, binding.dot6, binding.dot7, binding.dot8
        ).forEachIndexed { i, dot -> dot.isActivated = i < length }
    }

    private fun shakePin() {
        binding.pinView.animate()
            .translationX(14f).setDuration(60).withEndAction {
                binding.pinView.animate()
                    .translationX(-14f).setDuration(60).withEndAction {
                        binding.pinView.animate()
                            .translationX(0f).setDuration(60).start()
                    }.start()
            }.start()
    }
    // FIX: onKeyDown() removed — duplicate of OnBackPressedCallback
}