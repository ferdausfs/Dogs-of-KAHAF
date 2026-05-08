package com.guardian.shield.ui.setup

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityPinVerifyBinding
import com.guardian.shield.viewmodel.PinViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * FIX-LOG (vs original):
 *  - BUG #5: now returns RESULT_OK to the launching Activity, which truly gates
 *            access to MainActivity / SettingsActivity (caller hides UI until
 *            this Activity returns RESULT_OK).
 *  - BUG #9: replaced deprecated onBackPressed() with OnBackPressedDispatcher;
 *            on back press we cancel (RESULT_CANCELED) so the caller can decide
 *            to finish/finishAffinity instead of being silently bypassed.
 */
@AndroidEntryPoint
class PinVerifyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPinVerifyBinding
    private val vm: PinViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVerify.setOnClickListener {
            if (vm.verify(binding.etPin.text.toString())) {
                setResult(RESULT_OK)
                finish()
            } else {
                binding.tvError.text = "Wrong PIN"
            }
        }

        // BUG #9 fix — predictive-back-aware.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_CANCELED)
                finish()
            }
        })
    }
}
