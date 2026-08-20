package com.guardian.shield.ui.setup

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityPinRecoveryBinding
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.util.GuardianConstants
import com.guardian.shield.util.PinResetNotifier
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * PHASE 1c (v3.5.0) — PIN recovery screen.
 * Mock: guardian-redesign/mocks/oneui8/pin-recovery.html.
 *
 * Deliberately non-trivial (commitment-device principle, same spirit as the
 * confidence cooling-off system):
 *
 *  Path A — recovery code. The code was shown ONCE at PIN setup; only its
 *  salted PBKDF2 hash lives in the app. A correct code immediately clears the
 *  old PIN and routes to a fresh PIN setup (which issues a NEW code).
 *  Wrong codes are rate-limited (5 tries → 30 min lockout, PinManager).
 *
 *  Path B — time-delayed reset. Starts a 48h wall-clock wait. The whole
 *  window a persistent notification shows the fixed deadline; when the wait
 *  elapses a timer worker posts a "ready" alert and the user may finally
 *  reset here. Cancelling the wait requires the PIN itself.
 *
 * There is NO instant "forgot PIN → reset" shortcut — by design.
 */
@AndroidEntryPoint
class PinRecoveryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinRecoveryBinding

    @Inject lateinit var pinManager: PinManager

    private val handler = Handler(Looper.getMainLooper())
    private val statusTicker = object : Runnable {
        override fun run() {
            renderResetState()
            handler.postDelayed(this, 30_000L)
        }
    }

    /** Cancelling a timed reset requires the PIN itself. */
    private val cancelPinGate = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pinManager.cancelTimedReset()
            PinResetNotifier.cancelAll(this)
            PinResetNotifier.cancelReadyAlert(this)
            Timber.i("Timed reset cancelled after successful PIN verify")
            snack(getString(R.string.recovery_reset_cancelled))
        }
        renderResetState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinRecoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnVerifyCode.setOnClickListener { verifyRecoveryCode() }
        binding.btnStartReset.setOnClickListener { confirmStartTimedReset() }
        binding.btnCompleteReset.setOnClickListener { confirmCompleteTimedReset() }
        binding.btnCancelReset.setOnClickListener {
            cancelPinGate.launch(Intent(this, PinVerifyActivity::class.java))
        }

        renderResetState()
    }

    override fun onResume() {
        super.onResume()
        renderResetState()
        handler.post(statusTicker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusTicker)
    }

    // --- Path A: recovery code ---

    private fun verifyRecoveryCode() {
        val input = binding.editRecoveryCode.text?.toString().orEmpty()
        when (val r = pinManager.verifyRecoveryCode(input)) {
            PinManager.RecoveryResult.Success -> {
                Timber.w("Recovery code accepted — resetting PIN")
                pinManager.completeReset()
                PinResetNotifier.cancelAll(this)
                PinResetNotifier.cancelReadyAlert(this)
                startActivity(
                    Intent(this, PinSetupActivity::class.java).apply {
                        putExtra(PinSetupActivity.EXTRA_FRESH_SETUP, true)
                    }
                )
                finish()
            }
            is PinManager.RecoveryResult.Wrong -> showError(
                if (r.remainingAttempts >= 0)
                    getString(R.string.recovery_code_wrong_fmt, r.remainingAttempts)
                else getString(R.string.recovery_code_wrong_generic)
            )
            is PinManager.RecoveryResult.LockedOut -> showError(
                getString(R.string.recovery_code_locked_fmt, r.msRemaining / 60_000)
            )
            PinManager.RecoveryResult.NotSet -> showError(
                getString(R.string.recovery_code_not_set)
            )
        }
    }

    private fun showError(text: String) {
        binding.txtRecoveryError.text = text
        binding.txtRecoveryError.visibility = View.VISIBLE
    }

    // --- Path B: time-delayed reset ---

    private fun confirmStartTimedReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recovery_reset_confirm_title)
            .setMessage(R.string.recovery_reset_confirm_msg)
            .setPositiveButton(R.string.recovery_reset_confirm_yes) { _, _ ->
                pinManager.requestTimedReset()
                val deadline = pinManager.timedResetRequestedAt() +
                    GuardianConstants.PIN_RESET_DELAY_MS
                PinResetNotifier.showPending(this, deadline)
                PinResetNotifier.scheduleReadyAlert(this, deadline)
                Timber.w("48h timed reset armed via UI")
                renderResetState()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmCompleteTimedReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recovery_reset_finish_title)
            .setMessage(R.string.recovery_reset_finish_msg)
            .setPositiveButton(R.string.recovery_reset_complete) { _, _ ->
                pinManager.completeReset()
                PinResetNotifier.cancelAll(this)
                PinResetNotifier.cancelReadyAlert(this)
                Timber.w("48h timed reset completed via UI")
                startActivity(
                    Intent(this, PinSetupActivity::class.java).apply {
                        putExtra(PinSetupActivity.EXTRA_FRESH_SETUP, true)
                    }
                )
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renderResetState() {
        val requestedAt = pinManager.timedResetRequestedAt()
        when {
            requestedAt <= 0L -> {
                binding.txtResetStatus.text = getString(R.string.recovery_reset_idle)
                binding.txtResetDetail.text = getString(R.string.recovery_reset_idle_detail)
                binding.btnStartReset.visibility = View.VISIBLE
                binding.btnCompleteReset.visibility = View.GONE
                binding.btnCancelReset.visibility = View.GONE
            }
            pinManager.isTimedResetReady() -> {
                binding.txtResetStatus.text = getString(R.string.recovery_reset_ready)
                binding.txtResetDetail.text = getString(R.string.recovery_reset_ready_detail)
                binding.btnStartReset.visibility = View.GONE
                binding.btnCompleteReset.visibility = View.VISIBLE
                binding.btnCancelReset.visibility = View.GONE
            }
            else -> {
                val remaining = pinManager.timedResetRemainingMs()
                binding.txtResetStatus.text =
                    getString(R.string.recovery_reset_running_fmt, formatRemaining(remaining))
                binding.txtResetDetail.text = getString(R.string.recovery_reset_running_detail)
                binding.btnStartReset.visibility = View.GONE
                binding.btnCompleteReset.visibility = View.GONE
                binding.btnCancelReset.visibility = View.VISIBLE
            }
        }
    }

    private fun formatRemaining(ms: Long): String {
        val hours = ms / 3_600_000L
        val mins = (ms % 3_600_000L) / 60_000L
        return if (hours > 0) getString(R.string.overlay_dur_hours_minutes_fmt, hours, mins)
        else getString(R.string.overlay_dur_minutes_fmt, mins)
    }

    private fun snack(text: String) =
        Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).show()
}
