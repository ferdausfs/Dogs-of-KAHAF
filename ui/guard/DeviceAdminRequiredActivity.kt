package com.guardian.shield.ui.guard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.admin.GuardianDeviceAdminReceiver
import com.guardian.shield.databinding.ActivityDeviceAdminRequiredBinding
import com.guardian.shield.service.detection.TimeLockManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DeviceAdminRequiredActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceAdminRequiredBinding
    @Inject lateinit var timeLockManager: TimeLockManager

    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            if (isDeviceAdminActive()) {
                finish()
            } else {
                binding.txtRemaining.text = timeLockManager.getRemainingFormatted()
                handler.postDelayed(this, 2_000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        binding = ActivityDeviceAdminRequiredBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnReEnable.setOnClickListener { requestDeviceAdmin() }

        // Back press → home (DA re-enable না করলে dismiss না)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                runCatching {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        handler.post(checkRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(checkRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
    }

    private fun isDeviceAdminActive(): Boolean {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, GuardianDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(admin)
    }

    private fun requestDeviceAdmin() {
        val admin = ComponentName(this, GuardianDeviceAdminReceiver::class.java)
        runCatching {
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Commitment Lock সক্রিয়। ${timeLockManager.getRemainingFormatted()} পর্যন্ত Device Admin বাধ্যতামূলক।"
                    )
                }
            )
        }
    }
}