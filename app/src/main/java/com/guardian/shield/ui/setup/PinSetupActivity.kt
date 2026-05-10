package com.guardian.shield.ui.setup

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityPinSetupBinding
import com.guardian.shield.viewmodel.PinViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * FIX-LOG (vs original):
 *  - BUG #5: now returns RESULT_OK after a PIN is successfully set, so the
 *    launching Activity can reveal its UI only after a PIN actually exists.
 *  - BUG #9: predictive-back-aware cancel handler.
 *  - Stronger validation: numeric-only, 4–8 digits, no all-same-digit PINs.
 */
@AndroidEntryPoint
class PinSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPinSetupBinding
    private val vm: PinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSave.setOnClickListener {
            val a = binding.etPin.text.toString()
            val b = binding.etPinConfirm.text.toString()
            when {
                a.length < 4 || a.length > 8       -> binding.tvError.text = "PIN must be 4–8 digits"
                !a.all { it.isDigit() }            -> binding.tvError.text = "Digits only"
                a.toSet().size == 1                -> binding.tvError.text = "PIN cannot be all same digits"
                a != b                             -> binding.tvError.text = "PINs don't match"
                else -> {
                    vm.setPin(a)
                    setResult(RESULT_OK)
                    finish()
                }
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
