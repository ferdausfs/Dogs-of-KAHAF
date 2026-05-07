package com.guardian.shield.ui.setup

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityPinSetupBinding
import com.guardian.shield.viewmodel.PinViewModel
import dagger.hilt.android.AndroidEntryPoint

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
                a.length < 4 -> binding.tvError.text = "PIN must be at least 4 digits"
                a != b -> binding.tvError.text = "PINs don't match"
                else -> { vm.setPin(a); finish() }
            }
        }
    }
}
