package com.guardian.shield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardian.shield.service.blocker.GuardianForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        GuardianForegroundService.start(context)
    }
}
