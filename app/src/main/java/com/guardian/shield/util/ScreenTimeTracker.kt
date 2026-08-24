package com.guardian.shield.util

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Calendar

/**
 * R7.4 — Screen-time source backed by the ALREADY-DECLARED
 * PACKAGE_USAGE_STATS permission (previously dead: granted by users during
 * permission setup but never read anywhere). Pure read-only wrapper over
 * UsageStatsManager; every caller guards with
 * [PermissionManager.isUsageStatsGranted] first.
 */
object ScreenTimeTracker {

    /** Suggest blocking the top time-sink once it crossed ~45 min today. */
    const val SUGGEST_AFTER_MS: Long = 45L * 60 * 1000L

    data class AppUsage(
        val packageName: String,
        val label: String,
        val totalMs: Long
    )

    data class Summary(
        val totalMs: Long,
        /** per-app usage, descending; capped by caller's topN. */
        val top: List<AppUsage>
    )

    /**
     * Today's usage (local midnight → now), aggregated per launchable app,
     * excluding this app itself and home launchers. Call OFF the main thread.
     */
    fun summary(context: Context, topN: Int): Summary {
        val pm = context.packageManager
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return Summary(0L, emptyList())
        val (dayStart, now) = todayRange()
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, now)
        }.getOrNull() ?: return Summary(0L, emptyList())

        val homes = launcherPackages(pm)
        val self = context.packageName

        // One UsageStats entry per package per bucket — merge defensively.
        val perPkg = HashMap<String, Long>()
        stats.forEach { s ->
            if (s.totalTimeInForeground > 0L) {
                perPkg.merge(s.packageName, s.totalTimeInForeground, Long::plus)
            }
        }

        val list = perPkg.entries
            .filter { (pkg, ms) ->
                ms >= 60_000L && pkg != self && pkg !in homes &&
                    runCatching { pm.getLaunchIntentForPackage(pkg) != null }
                        .getOrDefault(false)
            }
            .map { (pkg, ms) ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg).ifBlank { pkg }
                AppUsage(pkg, label, ms)
            }
            .sortedByDescending { it.totalMs }

        val total = list.sumOf { it.totalMs }
        return Summary(total, list.take(topN))
    }

    private fun launcherPackages(pm: PackageManager): Set<String> = runCatching {
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).mapNotNull { it.activityInfo?.packageName }.toSet()
    }.getOrDefault(emptySet())

    private fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis to System.currentTimeMillis()
    }

    /** "4h 12m" / "37m" compact formatter shared by dashboard + detail screen. */
    fun formatMs(ms: Long): String {
        val totalMin = ms / 60_000L
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "%dh %02dm".format(h, m) else "${m}m"
    }
}
