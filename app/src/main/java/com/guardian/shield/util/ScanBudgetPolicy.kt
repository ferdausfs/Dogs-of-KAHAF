package com.guardian.shield.util

object ScanBudgetPolicy {
    private const val STALE_INTERACTION_MS = 10_000L
    private val LOW_VALUE_PREFIXES = listOf(
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher"
    )

    fun shouldRunTextScan(
        packageName: String,
        isSafePackage: Boolean,
        isBlockingInProgress: Boolean,
        lastTextScanAt: Long,
        now: Long,
        throttleMs: Long
    ): Boolean {
        if (packageName.isBlank() || isSafePackage || isBlockingInProgress) return false
        if (now - lastTextScanAt < throttleMs) return false
        return true
    }

    fun shouldRunHeavyScan(
        packageName: String,
        isSafePackage: Boolean,
        isScreenOn: Boolean,
        protectionEnabled: Boolean,
        isBlockingInProgress: Boolean,
        isBatteryLow: Boolean,
        isCharging: Boolean,
        lastInteractionAt: Long,
        now: Long
    ): Boolean {
        if (!isScreenOn || !protectionEnabled || isBlockingInProgress) return false
        if (packageName.isBlank() || isSafePackage) return false
        if (LOW_VALUE_PREFIXES.any { packageName == it || packageName.startsWith("$it.") }) return false
        if (isBatteryLow && !isCharging) return false
        if (now - lastInteractionAt > STALE_INTERACTION_MS) return false
        return true
    }
}
