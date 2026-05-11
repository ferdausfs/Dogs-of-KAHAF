package com.guardian.shield.ui.setup

import android.os.Bundle
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityPinSetupBinding
import com.guardian.shield.viewmodel.PinViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PinSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinSetupBinding
    private val viewModel: PinViewModel by viewModels()
    private val buffer = StringBuilder()
    private var firstPass: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        binding.btnOk.setOnClickListener {
            if (buffer.length < 4) {
                Snackbar.make(binding.root, R.string.pin_min_length, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (firstPass == null) {
                firstPass = buffer.toString()
                buffer.clear()
                refreshDots()
                binding.txtPrompt.setText(R.string.pin_confirm)
            } else {
                if (buffer.toString() == firstPass) {
                    val ok = viewModel.setPin(buffer.toString())
                    if (ok) {
                        Snackbar.make(binding.root, R.string.pin_saved, Snackbar.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Snackbar.make(binding.root, R.string.pin_invalid, Snackbar.LENGTH_SHORT).show()
                    }
                } else {
                    Snackbar.make(binding.root, R.string.pin_mismatch, Snackbar.LENGTH_SHORT).show()
                    firstPass = null
                    buffer.clear()
                    refreshDots()
                    binding.txtPrompt.setText(R.string.pin_enter)
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
