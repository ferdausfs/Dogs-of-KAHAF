package com.kahaf.guardian.ui.pin

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kahaf.guardian.databinding.ActivityPinSetupBinding
import com.kahaf.guardian.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PinSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinSetupBinding
    private val viewModel: PinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeEvents()
    }

    private fun setupListeners() {
        binding.btnSetPin.setOnClickListener {
            val pin = binding.etPin.text.toString()
            val confirmPin = binding.etConfirmPin.text.toString()
            viewModel.setPin(pin, confirmPin)
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is PinEvent.PinSetSuccess -> {
                        toast("PIN set successfully!")
                        finish()
                    }
                    is PinEvent.Error -> {
                        binding.tvError.visible()
                        binding.tvError.text = event.message
                    }
                    else -> {}
                }
            }
        }

        collectFlow(viewModel.isLoading) { isLoading ->
            binding.btnSetPin.isEnabled = !isLoading
        }
    }

    override fun onBackPressed() {
        // Don't allow going back without setting PIN
        toast("Please set a PIN to continue")
    }
}