package com.guardian.shield.ui.settings

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityTimeLockBinding
import com.guardian.shield.service.detection.TimeLockManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.chip1day.setOnClickListener { confirmLock("১ দিন", TimeLockManager.DURATIONS[0].second) }
        binding.chip3day.setOnClickListener { confirmLock("৩ দিন", TimeLockManager.DURATIONS[1].second) }
        binding.chip7day.setOnClickListener { confirmLock("৭ দিন", TimeLockManager.DURATIONS[2].second) }
        binding.chip15day.setOnClickListener { confirmLock("১৫ দিন", TimeLockManager.DURATIONS[3].second) }
        binding.chip30day.setOnClickListener { confirmLock("৩০ দিন", TimeLockManager.DURATIONS[4].second) }

        render()
    }

    private fun render() {
        timeLockManager.clearIfExpired()
        if (timeLockManager.isLocked()) {
            binding.groupLocked.visibility = View.VISIBLE
            binding.groupUnlocked.visibility = View.GONE
            binding.txtLockStatus.text = "🔒 Lock সক্রিয়"
            binding.txtLockEnd.text = "পর্যন্ত: ${timeLockManager.getEndTimeFormatted()}"
            startCountdown()
        } else {
            binding.groupLocked.visibility = View.GONE
            binding.groupUnlocked.visibility = View.VISIBLE
        }
    }

    private fun startCountdown() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(timeLockManager.getRemainingMs(), 60_000) {
            override fun onTick(ms: Long) { binding.txtRemaining.text = timeLockManager.getRemainingFormatted() }
            override fun onFinish() { timeLockManager.clearIfExpired(); render() }
        }.start()
        binding.txtRemaining.text = timeLockManager.getRemainingFormatted()
    }

    private fun confirmLock(label: String, durationMs: Long) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ নিশ্চিত করুন")
            .setMessage(
                "$label এর জন্য সমস্ত settings lock হয়ে যাবে।\n\n" +
                "এই সময়ে:\n• কোনো setting বন্ধ করা যাবে না\n" +
                "• App uninstall করা যাবে না\n• কোনোভাবেই unlock করা যাবে না\n\nআপনি কি নিশ্চিত?"
            )
            .setCancelable(false)
            .setPositiveButton("হ্যাঁ, Lock করুন") { _, _ ->
                timeLockManager.setLock(durationMs, label)
                Toast.makeText(this, "✅ $label এর জন্য lock করা হয়েছে", Toast.LENGTH_LONG).show()
                render()
            }
            .setNegativeButton("না") { _, _ -> }
            .show()
    }

    override fun onDestroy() { super.onDestroy(); countDownTimer?.cancel() }
}