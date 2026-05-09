package com.kahaf.guardianshield.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kahaf.guardianshield.service.foreground.GuardianForegroundService
import com.kahaf.guardianshield.service.worker.GuardianWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Resumes protection after device boot or app update.
 *
 *  - BOOT_COMPLETED / LOCKED_BOOT_COMPLETED → start FG service + reschedule work
 *  - MY_PACKAGE_REPLACED → same, after self-update
 *
 * The AccessibilityService itself is auto-restored by the OS once the user has
 * granted Accessibility access; we just need to bring the FG service back so
 * the WorkManager pipeline & TimedBlockManager ticker resume.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var workScheduler: GuardianWorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action ?: return
            when (action) {
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    Log.i(TAG, "Resuming Guardian Shield after $action")
                    GuardianForegroundService.start(context)
                    workScheduler.scheduleAll()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onReceive error", t)
        }
    }

    companion object { private const val TAG = "GuardianBoot" }
}
