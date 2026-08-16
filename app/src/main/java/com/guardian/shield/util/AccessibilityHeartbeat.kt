package com.guardian.shield.util

import android.os.SystemClock

/**
 * In-process liveness signal shared between [com.guardian.shield.service.accessibility.GuardianAccessibilityService]
 * (producer) and [com.guardian.shield.service.blocker.GuardianForegroundService] (consumer).
 *
 * The watchdog can only see the *setting* `ENABLED_ACCESSIBILITY_SERVICES`, which
 * stays "on" even after the accessibility service's process/connection dies.
 * This heartbeat lets the watchdog distinguish "enabled and alive" from
 * "enabled but dead" so it can re-prompt the user to restart protection.
 */
object AccessibilityHeartbeat {

    @Volatile
    private var lastBeatElapsed: Long = 0L

    fun beat() {
        lastBeatElapsed = SystemClock.elapsedRealtime()
    }

    /** Milliseconds since the last beat, or [Long.MAX_VALUE] if never beaten. */
    fun sinceLastBeatMs(): Long {
        val last = lastBeatElapsed
        if (last == 0L) return Long.MAX_VALUE
        return SystemClock.elapsedRealtime() - last
    }
}
