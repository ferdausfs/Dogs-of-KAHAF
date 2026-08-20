package com.guardian.shield.ui.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.guardian.shield.R
import com.guardian.shield.databinding.ActivityBlockOverlayBinding
import com.guardian.shield.service.blocker.PendingReportManager
import com.guardian.shield.service.blocker.TempBlockManager
import com.guardian.shield.util.GuardianConstants
import com.guardian.shield.service.detection.ConfirmedSensitiveMemory
import com.guardian.shield.service.detection.FalsePositiveMemory
import com.guardian.shield.ui.unlock.DelayUnlockActivity
import com.guardian.shield.util.ReligiousReminders
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class BlockOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockOverlayBinding

    @Inject lateinit var falsePositiveMemory: FalsePositiveMemory

    @Inject lateinit var confirmedSensitiveMemory: ConfirmedSensitiveMemory

    @Inject lateinit var tempBlockManager: TempBlockManager

    @Inject lateinit var pendingReportManager: PendingReportManager

    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        val detail = intent.getStringExtra(EXTRA_DETAIL).orEmpty()
        // TASK A — read the AI confidence score threaded from the detection.
        val confidence = intent.getFloatExtra(EXTRA_CONFIDENCE, -1f)

        binding.txtPackage.text = pkg

        // v3.3.0 — rotating Qur'anic ayat + Bengali translation. Pure content
        // addition; Stay Protected / Unlock / Mark False flow is unchanged.
        // findViewById (not generated binding fields) so a merge-<include>
        // flattening difference across AGP versions cannot break onCreate.
        ReligiousReminders.bind(
            findViewById<TextView>(R.id.txtAyatArabic),
            findViewById<TextView>(R.id.txtAyatBengali),
            findViewById<TextView>(R.id.txtAyatCitation)
        )

        // Temp-block branch (incl. TASK 3 — 24h hard lock formatting).
        // AI-originated temp blocks are tagged "temp_block:NNmin;ai" so we can
        // distinguish them from a plain app-block enforcement (which also uses
        // reason=APP_BLOCKED but has no AI-frame candidate to learn from).
        val isTempBlock = detail.startsWith("temp_block:")
        val isAiBlock = reason == "AI_DETECTION" || detail.endsWith(";ai")
        // Premium redesign — additional optional views, safe to ignore if not in binding
        try {
            binding.txtCategory.text = when (reason) {
                "AI_DETECTION" -> getString(R.string.overlay_category_ai)
                "KEYWORD_MATCH" -> getString(R.string.overlay_category_keyword, detail.take(20))
                "APP_BLOCKED" -> if (isAiBlock) getString(R.string.overlay_category_ai)
                    else getString(R.string.overlay_category_app)
                "SCHEDULE_BLOCKED" -> getString(R.string.overlay_category_schedule)
                else -> getString(R.string.overlay_category_generic)
            }
        } catch (_: Throwable) {}

        // TASK A — display real AI confidence score as a small badge on the
        // full-block screen. Only shown for AI blocks with a real score.
        if (isAiBlock && confidence >= 0f) {
            try {
                binding.txtConfidenceBadge.text = getString(R.string.ai_confidence_badge_fmt, confidence)
                binding.txtConfidenceBadge.visibility = View.VISIBLE
            } catch (_: Throwable) {}
        }

        if (isTempBlock) {
            val raw = detail.removePrefix("temp_block:")
                .substringBefore(";")
                .removeSuffix("min")
                .trim()
            val mins = raw.toLongOrNull() ?: 0L
            val displayText = formatDuration(mins)
            binding.txtReason.text = getString(R.string.overlay_temp_block_fmt, displayText)
            binding.txtReason.setTextColor(getColor(R.color.error))
            // Premium: show temp banner card if present
            try {
                binding.cardTempBlock.visibility = View.VISIBLE
                binding.txtTempBanner.text = getString(R.string.overlay_temp_block_fmt, displayText) + " • ${displayText}"
            } catch (_: Throwable) {}
            // Hard lock — no unlock option
            binding.btnUnlock.visibility = View.GONE
        } else {
            binding.txtReason.text = formatReason(reason, detail)
            binding.txtReason.setTextColor(getColor(R.color.warning_amber))
            try {
                binding.cardTempBlock.visibility = View.GONE
            } catch (_: Throwable) {}
            binding.btnUnlock.visibility = View.VISIBLE
            binding.btnUnlock.setOnClickListener {
                startActivity(Intent(this, DelayUnlockActivity::class.java).apply {
                    putExtra(DelayUnlockActivity.EXTRA_PACKAGE, pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                finish()
            }
        }

        binding.btnHome.setOnClickListener { goHome() }

        // LEARNING MEMORY — only AI blocks can be "false blocks". AI 3rd-strike
        // blocks reach here with reason=APP_BLOCKED but detail tagged ";ai", so
        // gate on isAiBlock rather than the reason string. Non-AI temp blocks
        // (app/schedule enforcement) have no remembered candidate and must not
        // show this button.
        if (isAiBlock) {
            binding.btnMarkFalse.visibility = View.VISIBLE
            binding.btnMarkFalse.setOnClickListener {
                // v3.6.0 — CONFIRMED-SENSITIVE REFUSAL OVERRIDE. Runs BEFORE the
                // confidence-based cooling-off branching and takes priority over
                // everything else in the report flow (including the 0.82
                // threshold and the escalating-delay queue): if the current
                // candidate pattern is protected, the report is refused
                // outright — no clearTempBlock, no cooling-off enqueue, no
                // addSignature — regardless of the live confidence score. The
                // block stays active and the user sees honest feedback. The
                // candidate is PEEKED (not consumed) so a repeated tap shows
                // the same refusal instead of silently falling through on a
                // null candidate.
                val candidate = falsePositiveMemory.peekPendingCandidate()
                if (candidate != null && confirmedSensitiveMemory.isConfirmedSignature(candidate)) {
                    Timber.w("Mark False REFUSED for $pkg — pattern is confirmed-sensitive (protected)")
                    try {
                        binding.txtCoolingStatus.text = getString(R.string.overlay_report_refused_confirmed)
                        binding.txtCoolingStatus.setTextColor(getColor(R.color.error))
                        binding.txtCoolingStatus.visibility = View.VISIBLE
                    } catch (_: Throwable) {}
                    Snackbar.make(binding.root, R.string.overlay_report_refused_confirmed, Snackbar.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                // Task B — confidence-based branching for the full-block "Mark False".
                if (pendingReportManager.isHighConfidence(confidence)) {
                    // HIGH confidence → defer the unblock via cooling-off queue.
                    // The block screen stays visible; the user sees delay messaging.
                    binding.btnMarkFalse.isEnabled = false
                    binding.btnMarkFalse.text = getString(R.string.cooling_mark_false_queued)

                    // Snapshot the candidate NOW so the worker learns THIS
                    // pattern hours later, not whatever pendingCandidate holds
                    // at apply time (and so we don't steal a later card's candidate).
                    val sig = falsePositiveMemory.takePendingCandidate()
                    workerScope.launch {
                        try {
                            // NonCancellable: persist even if the overlay is
                            // destroyed (Stay Protected) while enqueue is in flight.
                            val result = withContext(NonCancellable) {
                                pendingReportManager.enqueue(
                                    pkg = pkg,
                                    confidence = confidence,
                                    source = PendingReportManager.Source.FULL_BLOCK,
                                    strikeCount = GuardianConstants.STRIKE_THRESHOLD,
                                    signature = sig
                                )
                            }
                            val applyTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                                .format(Date(result.scheduledApplyAt))
                            val msg = getString(R.string.cooling_full_block_queued_fmt, applyTime)
                            runOnUiThread {
                                // Show the delay messaging on the block screen so the user
                                // understands why tapping didn't unblock them.
                                try {
                                    binding.txtCoolingStatus.text = msg
                                    binding.txtCoolingStatus.visibility = View.VISIBLE
                                } catch (_: Throwable) {}
                                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                            }
                            Timber.i("Mark False HIGH-conf queued: pkg=$pkg delay=${result.delayMs / 60_000}min")
                        } catch (t: Throwable) {
                            Timber.e(t, "Failed to enqueue pending FULL_BLOCK for $pkg")
                            if (sig != null) falsePositiveMemory.rememberCandidate(sig)
                            runOnUiThread {
                                binding.btnMarkFalse.isEnabled = true
                                binding.btnMarkFalse.text = getString(R.string.overlay_mark_false)
                                Snackbar.make(binding.root, R.string.cooling_enqueue_failed, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    // LOW confidence → apply immediately (existing behavior).
                    // BUG D — the unblock (clearTempBlock + relaunch) runs
                    // UNCONDITIONALLY on every tap and must NEVER depend on whether a
                    // pattern signature happened to survive in memory. The old code
                    // gated clearTempBlock + relaunch behind `sig != null`, so when
                    // takePendingCandidate() came back null the button did nothing
                    // but show an "unavailable" Snackbar and the user stayed blocked
                    // ("ব্লক হবার পর ভুল ব্লক ক্লিক করলে কাজ করে না"). That null case
                    // is real: pendingCandidate is a plain @Volatile in-memory field
                    // of a @Singleton, so it is lost on process death/restart between
                    // the detection and the tap (the overlay activity can be recreated
                    // from its persisted intent extras with a freshly-initialised,
                    // candidate-less FalsePositiveMemory).
                    tempBlockManager.clearTempBlock(pkg)
                    binding.btnMarkFalse.isEnabled = false
                    binding.btnMarkFalse.text = getString(R.string.overlay_mark_false_done)
                    Snackbar.make(binding.root, R.string.overlay_mark_false_done, Snackbar.LENGTH_LONG).show()

                    // Learning the pattern is best-effort and fully independent of the
                    // unblock above: if no candidate survived we simply don't learn,
                    // and the user is still unblocked either way.
                    val sig = falsePositiveMemory.takePendingCandidate()
                    if (sig != null) {
                        falsePositiveMemory.addSignature(sig)
                    } else {
                        Timber.w("Mark False: no pending candidate signature to learn from (unblock still applied)")
                    }

                    relaunchBlockedApp(pkg)
                }
            }
        }

        vibrate()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })
    }

    /**
     * Bug C — after the active temp block is cleared on a false-block report,
     * auto-relaunch the app the user was blocked from via its launcher intent.
     *
     * - Uses [android.content.pm.PackageManager.getLaunchIntentForPackage] so the
     *   target app opens with its normal MAIN/LAUNCHER intent.
     * - If the launch intent is null (app uninstalled / no launcher activity) we
     *   still finish the overlay and tell the user the app was unblocked.
     * - [android.content.pm.PackageManager] can throw (e.g. app removed between
     *   the block and the tap); the whole block is wrapped in try/catch so a
     *   failure can never crash the overlay — we finish gracefully either way.
     */
    private fun relaunchBlockedApp(pkg: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                finish()
            } else {
                Timber.w("No launch intent for $pkg — app unblocked, closing overlay")
                Toast.makeText(this, R.string.overlay_app_unblocked, Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (t: Throwable) {
            Timber.e(t, "Failed to relaunch $pkg after false-block report — unblocking anyway")
            runCatching {
                Toast.makeText(this, R.string.overlay_app_unblocked, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    /**
     * Render a duration (in minutes) in Bangla:
     *   1440 → "২৪ ঘন্টা"
     *   90   → "১ ঘন্টা ৩০ মিনিট"
     *   45   → "৪৫ মিনিট"
     */
    private fun formatDuration(mins: Long): String {
        if (mins <= 0) return getString(R.string.overlay_dur_short)
        if (mins >= 60) {
            val hours = mins / 60
            val remaining = mins % 60
            return if (remaining > 0) getString(R.string.overlay_dur_hours_minutes_fmt, hours, remaining)
            else getString(R.string.overlay_dur_hours_fmt, hours)
        }
        return getString(R.string.overlay_dur_minutes_fmt, mins)
    }

    private fun goHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
        finish()
    }

    private fun formatReason(reason: String, detail: String): String = when (reason) {
        "AI_DETECTION" -> getString(R.string.overlay_reason_ai)
        "KEYWORD_MATCH" -> getString(R.string.overlay_reason_kw, detail)
        "APP_BLOCKED" -> getString(R.string.overlay_reason_app)
        "SCHEDULE_BLOCKED" -> getString(R.string.overlay_reason_sched)
        "TAMPER_ATTEMPT" -> getString(R.string.overlay_reason_tamper)
        else -> getString(R.string.overlay_reason_manual)
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                    ?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        workerScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_DETAIL = "extra_detail"
        // TASK A — AI detection confidence score (0..1 float) threaded from the
        // detection to the overlay for badge display and Task B gating.
        const val EXTRA_CONFIDENCE = "extra_confidence"
    }
}
