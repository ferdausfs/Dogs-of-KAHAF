package com.guardian.shield.ui.setup

import android.os.Bundle
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityPinVerifyBinding
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.viewmodel.PinViewModel
import dagger.hilt.android.AndroidEntryPoint
import com.guardian.shield.util.ScreenInsets

@AndroidEntryPoint
class PinVerifyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinVerifyBinding
    private val viewModel: PinViewModel by viewModels()
    private val buffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ScreenInsets.padTopForStatusBar(binding.root)

        val digits = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9
        )
        digits.forEachIndexed { i, b ->
            (b as Button).setOnClickListener {
                if (buffer.length < 6) {
                    buffer.append(i.toString())
                    refreshDots()
                }
            }
        }
        binding.btnDel.setOnClickListener {
            if (buffer.isNotEmpty()) {
                buffer.deleteCharAt(buffer.length - 1)
                refreshDots()
            }
        }
        // PHASE 1c (v3.5.0) — "পিন ভুলে গেছেন?" opens the deliberately
        // non-trivial recovery flow (recovery code OR a 48h timed reset).
        binding.txtForgot.setOnClickListener {
            startActivity(android.content.Intent(this, PinRecoveryActivity::class.java))
        }

        binding.btnOk.setOnClickListener {
            when (val r = viewModel.verifyPin(buffer.toString())) {
                PinManager.VerifyResult.Success -> {
                    setResult(RESULT_OK); finish()
                }
                is PinManager.VerifyResult.Wrong -> {
                    Snackbar.make(binding.root,
                        getString(R.string.pin_wrong_attempts, r.remainingAttempts),
                        Snackbar.LENGTH_SHORT).show()
                    buffer.clear(); refreshDots()
                }
                is PinManager.VerifyResult.LockedOut -> {
                    Snackbar.make(binding.root,
                        getString(R.string.pin_locked, r.msRemaining / 1000),
                        Snackbar.LENGTH_LONG).show()
                    buffer.clear(); refreshDots()
                }
                PinManager.VerifyResult.NotSet -> {
                    Snackbar.make(binding.root, R.string.pin_not_set, Snackbar.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun refreshDots() {
        val len = buffer.length
        listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4, binding.dot5, binding.dot6)
            .forEachIndexed { i, v ->
                v.setBackgroundResource(
                    if (i < len) R.drawable.dot_filled else R.drawable.dot_empty
                )
            }
    }
}
