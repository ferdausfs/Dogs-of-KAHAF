package com.guardian.shield.ui.setup

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityPinVerifyBinding
import com.guardian.shield.viewmodel.PinViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PinVerifyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPinVerifyBinding
    private val vm: PinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnVerify.setOnClickListener {
            if (vm.verify(binding.etPin.text.toString())) finish()
            else binding.tvError.text = "Wrong PIN"
        }
    }

    @Suppress("MissingSuperCall")
    override fun onBackPressed() { finishAffinity() }
}
