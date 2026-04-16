package com.kahaf.guardian.ui.pin

import android.app.Activity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kahaf.guardian.databinding.ActivityPinVerifyBinding
import com.kahaf.guardian.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PinVerifyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPinVerifyBinding
    private val vm: PinViewModel by viewModels()
    companion object { const val RESULT_PIN_VERIFIED = Activity.RESULT_OK }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnVerify.setOnClickListener { val p = binding.etPin.text.toString(); if (p.isNotEmpty()) vm.verifyPin(p) }
        lifecycleScope.launch { vm.events.collect { when (it) { is PinEvent.PinVerifySuccess -> { setResult(RESULT_PIN_VERIFIED); finish() }; is PinEvent.Error -> { binding.tvError.visible(); binding.tvError.text = it.message; binding.etPin.text?.clear() }; else -> {} } } }
        collectFlow(vm.isLoading) { binding.btnVerify.isEnabled = !it }
    }
}
