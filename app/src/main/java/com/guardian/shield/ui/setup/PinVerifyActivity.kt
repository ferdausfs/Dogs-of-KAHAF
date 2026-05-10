package com.guardian.shield.ui.setup

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityPinVerifyBinding
import com.guardian.shield.viewmodel.PinViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * v12 (2.1.2):
 *  • Debounce: disable Verify button after first tap until result returns
 *    (prevents double-finish IllegalStateException on slow devices).
 *  • Defensive: PinManager.verifyPin can throw on broken Keystore — wrap.
 */
@AndroidEntryPoint
class PinVerifyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPinVerifyBinding
    private val vm: PinViewModel by viewModels()

    @Volatile private var verifying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVerify.setOnClickListener {
            if (verifying) return@setOnClickListener
            verifying = true
            binding.btnVerify.isEnabled = false

            val ok = runCatching { vm.verify(binding.etPin.text.toString()) }
                .getOrDefault(false)

            if (ok) {
                setResult(RESULT_OK)
                finish()
            } else {
                binding.tvError.text = "Wrong PIN"
                verifying = false
                binding.btnVerify.isEnabled = true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_CANCELED)
                finish()
            }
        })
    }
}
