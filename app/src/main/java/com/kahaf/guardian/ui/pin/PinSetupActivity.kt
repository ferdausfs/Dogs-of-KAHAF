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
    private val vm: PinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnSetPin.setOnClickListener { vm.setPin(binding.etPin.text.toString(), binding.etConfirmPin.text.toString()) }
        lifecycleScope.launch { vm.events.collect { when (it) { is PinEvent.PinSetSuccess -> { toast("PIN set!"); finish() }; is PinEvent.Error -> { binding.tvError.visible(); binding.tvError.text = it.message }; else -> {} } } }
        collectFlow(vm.isLoading) { binding.btnSetPin.isEnabled = !it }
    }

    @Deprecated("Deprecated") override fun onBackPressed() { toast("Please set a PIN") }
}
