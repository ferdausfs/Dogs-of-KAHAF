package com.guardian.shield.ui.unlock

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.databinding.ActivityDelayUnlockBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DelayUnlockActivity : AppCompatActivity() {

    @Inject lateinit var prefs: GuardianPreferences
    private lateinit var binding: ActivityDelayUnlockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDelayUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            val total = prefs.delaySeconds.first()
            for (s in total downTo 1) {
                binding.tvCounter.text = s.toString()
                binding.tvHint.text = "Reflect for $s seconds…"
                delay(1000)
            }
            binding.tvCounter.text = "0"
            binding.tvHint.text = "You may now resume — but think twice."
            binding.btnDone.isEnabled = true
        }
        binding.btnDone.isEnabled = false
        binding.btnDone.setOnClickListener { finish() }
    }
}
