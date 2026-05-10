package com.guardian.shield.ui.setup

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityPinSetupBinding
import com.guardian.shield.viewmodel.PinViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * v12 (2.1.2):
 *  • Disable Save button while a save is in flight (debounce double-tap that
 *    could trigger duplicate finish() and IllegalStateException).
 *  • Trim error text to one line — multi-line layouts on small screens
 *    were pushing fields off-screen.
 *
 * v11 (2.1.1) and earlier kept verbatim.
 */
@AndroidEntryPoint
class PinSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPinSetupBinding
    private val vm: PinViewModel by viewModels()

    @Volatile private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSave.setOnClickListener {
            if (saving) return@setOnClickListener
            val a = binding.etPin.text.toString()
            val b = binding.etPinConfirm.text.toString()
            when {
                a.length < 4 || a.length > 8 -> binding.tvError.text = "PIN must be 4–8 digits"
                !a.all { it.isDigit() }      -> binding.tvError.text = "Digits only"
                a.toSet().size == 1          -> binding.tvError.text = "PIN cannot be all same digits"
                a != b                       -> binding.tvError.text = "PINs don't match"
                else -> {
                    saving = true
                    binding.btnSave.isEnabled = false
                    runCatching { vm.setPin(a) }
                        .onFailure {
                            saving = false
                            binding.btnSave.isEnabled = true
                            binding.tvError.text = "Could not save PIN — try again"
                            return@setOnClickListener
                        }
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
