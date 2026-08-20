package com.guardian.shield.ui.setup

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityPinSetupBinding
import com.guardian.shield.viewmodel.PinViewModel
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class PinSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinSetupBinding
    private val viewModel: PinViewModel by viewModels()
    private val buffer = StringBuilder()
    private var firstPass: String? = null

    companion object {
        /** PHASE 1c — set when arriving from a recovery reset (copy nuance only). */
        const val EXTRA_FRESH_SETUP = "extra_fresh_setup"
    }

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
                        // PHASE 1c (v3.5.0) — after every successful PIN set,
                        // issue and reveal the one-time recovery code. The PIN
                        // is not "done" until the user confirms they saved it.
                        revealRecoveryCode()
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

    // ---------------------------------------------------------------------
    // PHASE 1c (v3.5.0) — one-time recovery-code reveal.
    //
    // A fresh code is generated on every successful PIN set/change. Only its
    // salted PBKDF2 hash is stored (PinManager); the plaintext exists only in
    // this panel. The screen cannot close until the user ticks "I wrote it
    // down" and taps Continue (or presses Back, which repeats the warning).
    // ---------------------------------------------------------------------
    private fun revealRecoveryCode() {
        val code = viewModel.generateRecoveryCode()
        if (code == null) {
            // Fail closed on storage failure — same policy as setPin, just finish.
            Timber.e("Recovery code generation failed (secure storage unavailable)")
            finish()
            return
        }
        binding.setupPanel.visibility = View.GONE
        binding.recoveryPanel.visibility = View.VISIBLE
        binding.txtRecoveryCode.text = code
        binding.chkCodeSaved.isChecked = false
        binding.btnRecoveryContinue.isEnabled = false

        binding.btnCopyCode.setOnClickListener {
            runCatching {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("recovery_code", code))
                Snackbar.make(binding.root, R.string.recovery_code_copied, Snackbar.LENGTH_SHORT).show()
            }.onFailure { Timber.w(it, "Copy recovery code failed") }
        }
        binding.chkCodeSaved.setOnCheckedChangeListener { _, checked ->
            binding.btnRecoveryContinue.isEnabled = checked
        }
        binding.btnRecoveryContinue.setOnClickListener {
            if (binding.chkCodeSaved.isChecked) finish()
        }
    }

    override fun onBackPressed() {
        if (binding.recoveryPanel.visibility == View.VISIBLE) {
            // Extra friction: leaving without confirming keeps the warning loud.
            Snackbar.make(
                binding.root, R.string.recovery_save_first_warning, Snackbar.LENGTH_LONG
            ).show()
            return
        }
        super.onBackPressed()
    }
}
