package com.guardian.shield.ui.settings

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityTimeLockBinding
import com.guardian.shield.service.detection.TimeLockManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.guardian.shield.util.ScreenInsets

@AndroidEntryPoint
class TimeLockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimeLockBinding
    private var countDownTimer: CountDownTimer? = null
    @Inject lateinit var timeLockManager: TimeLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimeLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        ScreenInsets.padTopForStatusBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.chip1day.setOnClickListener  { confirmLock("১ দিন",   TimeLockManager.DURATIONS[0].second) }
        binding.chip3day.setOnClickListener  { confirmLock("৩ দিন",   TimeLockManager.DURATIONS[1].second) }
        binding.chip7day.setOnClickListener  { confirmLock("৭ দিন",   TimeLockManager.DURATIONS[2].second) }
        binding.chip15day.setOnClickListener { confirmLock("১৫ দিন", TimeLockManager.DURATIONS[3].second) }
        binding.chip30day.setOnClickListener { confirmLock("৩০ দিন", TimeLockManager.DURATIONS[4].second) }

        binding.btnRequestUnlock.setOnClickListener { confirmUnlockRequest() }

        render()
    }

    private fun render() {
        timeLockManager.clearIfExpired()
        countDownTimer?.cancel()

        when {
            // ── COOLDOWN: unlock request দেওয়া হয়েছে, countdown চলছে ──
            timeLockManager.isInCooldown() -> {
                binding.groupUnlocked.visibility = View.GONE
                binding.groupLocked.visibility   = View.VISIBLE
                binding.txtLockStatus.text       = "⏳ Unlock Cooldown চলছে"
                binding.txtLockEnd.text          =
                    "Unlock হবে: ${timeLockManager.getCooldownEndFormatted()}"
                binding.txtLockLabel.text        =
                    "Cooldown: ${timeLockManager.getCooldownLabel()}"
                binding.btnRequestUnlock.visibility = View.GONE
                binding.txtCooldownNote.visibility  = View.VISIBLE
                startCooldownTimer()
            }

            // ── LOCKED: lock active, unlock request দেওয়া হয়নি ──
            timeLockManager.isLocked() -> {
                binding.groupUnlocked.visibility = View.GONE
                binding.groupLocked.visibility   = View.VISIBLE
                binding.txtLockStatus.text       = "🔒 Commitment Lock সক্রিয়"
                binding.txtLockEnd.text          =
                    "Cooldown: ${timeLockManager.getCooldownLabel()} (request দিলে শুরু হবে)"
                binding.txtLockLabel.text        = timeLockManager.getLockLabel()
                binding.btnRequestUnlock.visibility = View.VISIBLE
                binding.txtCooldownNote.visibility  = View.GONE
            }

            // ── UNLOCKED ──
            else -> {
                binding.groupUnlocked.visibility = View.VISIBLE
                binding.groupLocked.visibility   = View.GONE
            }
        }
    }

    private fun startCooldownTimer() {
        countDownTimer = object : CountDownTimer(timeLockManager.getCooldownRemainingMs(), 30_000) {
            override fun onTick(ms: Long) {
                binding.txtRemaining.text = timeLockManager.getCooldownRemainingMs().let { rem ->
                    val days  = rem / (24 * 60 * 60 * 1_000L)
                    val hours = (rem % (24 * 60 * 60 * 1_000L)) / (60 * 60 * 1_000L)
                    val mins  = (rem % (60 * 60 * 1_000L)) / (60 * 1_000L)
                    when {
                        days > 0  -> "${days} দিন ${hours} ঘণ্টা ${mins} মিনিট বাকি"
                        hours > 0 -> "${hours} ঘণ্টা ${mins} মিনিট বাকি"
                        else      -> "${mins} মিনিট বাকি"
                    }
                }
            }
            override fun onFinish() {
                timeLockManager.clearIfExpired()
                render()
            }
        }.start()
    }

    // ── Lock set করো ─────────────────────────────────────────────

    private fun confirmLock(label: String, durationMs: Long) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ নিশ্চিত করুন")
            .setMessage(
                "$label Commitment Lock করবেন?\n\n" +
                "• Lock indefinite — auto-expire হবে না\n" +
                "• Unlock করতে চাইলে request দিতে হবে\n" +
                "• Request দেওয়ার পর $label cooldown শুরু হবে\n" +
                "• Cooldown শেষে lock উঠবে\n\n" +
                "আপনি কি নিশ্চিত?"
            )
            .setCancelable(false)
            .setPositiveButton("হ্যাঁ, Lock করুন") { _, _ ->
                timeLockManager.setLock(durationMs, label)
                render()
            }
            .setNegativeButton("না") { _, _ -> }
            .show()
    }

    // ── Unlock request ────────────────────────────────────────────

    private fun confirmUnlockRequest() {
        val cooldownLabel = timeLockManager.getCooldownLabel()
        AlertDialog.Builder(this)
            .setTitle("🔓 Unlock Request")
            .setMessage(
                "Unlock request দিলে $cooldownLabel এর cooldown শুরু হবে।\n\n" +
                "এই সময়ে:\n" +
                "• Settings এখনো locked থাকবে\n" +
                "• Protection বন্ধ করা যাবে না\n" +
                "• $cooldownLabel পর স্বয়ংক্রিয়ভাবে unlock হবে\n\n" +
                "Request দিতে চান?"
            )
            .setCancelable(false)
            .setPositiveButton("হ্যাঁ, Request করুন") { _, _ ->
                timeLockManager.requestUnlock()
                render()
            }
            .setNegativeButton("না") { _, _ -> }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
