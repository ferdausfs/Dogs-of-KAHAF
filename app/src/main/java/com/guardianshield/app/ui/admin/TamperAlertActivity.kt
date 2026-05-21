package com.guardianshield.app.ui.admin

import android.app.Activity
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import com.guardianshield.app.GuardianApp
import com.guardianshield.app.data.model.ActivityLog
import com.guardianshield.app.databinding.ActivityTamperAlertBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TamperAlertActivity : Activity() {

    private lateinit var b: ActivityTamperAlertBinding
    private var ringtone: android.media.Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTamperAlertBinding.inflate(layoutInflater)
        setContentView(b.root)

        val reason = intent.getStringExtra("reason") ?: "tamper"
        b.txtReason.text = "Reason: $reason"

        // Alarm sound + vibrate
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, alarmUri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            play()
        }
        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 500), 0)
        )

        b.btnDismiss.setOnClickListener { finish() }

        CoroutineScope(Dispatchers.IO).launch {
            GuardianApp.get().repository.log(
                ActivityLog(packageName = packageName, eventType = "TAMPER", details = reason)
            )
        }
    }

    override fun onDestroy() {
        ringtone?.stop()
        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.cancel()
        super.onDestroy()
    }

    override fun onBackPressed() { /* cannot dismiss with back */ }
}
