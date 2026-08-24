package com.guardian.shield.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.guardian.shield.GuardianApp
import com.guardian.shield.R
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.repository.RulesRepository
import com.guardian.shield.ui.dashboard.MainActivity
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Calendar

/**
 * R7.6 — Weekly report + real accountability sharing.
 *
 *  - Every Sunday the daily [weekly worker][com.guardian.shield.service.blocker.WeeklyReportWorker]
 *    posts ONE digest notification (deduped per ISO week) built from the
 *    existing [StreakCalculator] + block_events history — no new tables.
 *  - Both the digest and the new dashboard "Share report" action open the
 *    system SHARE sheet, so the report can land in a real partner's
 *    WhatsApp/Telegram with zero backend.
 */
object WeeklyReporter {

    private const val PREFS = "weekly_report_cache"
    private const val C_LAST_SENT_WEEK = "last_sent_week"
    private const val NOTIF_ID = 1350

    /** Human-readable weekly digest (localized) from raw events. */
    fun buildText(context: Context, events: List<BlockEvent>, installDayStart: Long): String {
        val info = StreakCalculator.compute(events, floorDayStart = installDayStart)
        return context.getString(
            R.string.weekly_report_body_fmt,
            info.streakDays,
            info.thisWeekBlocks,
            info.lastWeekBlocks
        )
    }

    fun installDayStart(context: Context): Long = StreakCalculator.localDayStart(
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrDefault(0L)
    )

    suspend fun fetchReportText(context: Context, repo: RulesRepository): String {
        val windowStart = System.currentTimeMillis() - 400L * 24 * 60 * 60 * 1_000L
        val events = runCatching { repo.observeEventsSince(windowStart).first() }
            .getOrDefault(emptyList())
        return buildText(context, events, installDayStart(context))
    }

    /** System share sheet with the weekly digest — used by digest + dashboard. */
    fun share(context: Context, title: String, body: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n\n$body")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Timber.e(it, "WeeklyReporter: share failed") }
    }

    /**
     * Sunday-only, once-per-week digest notification. No-ops silently any
     * other day / when already sent this week / notifications not granted.
     */
    suspend fun maybeNotify(context: Context, repo: RulesRepository) {
        val now = Calendar.getInstance()
        if (now.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) return
        val weekKey = now.get(Calendar.YEAR) * 100 + now.get(Calendar.WEEK_OF_YEAR)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(C_LAST_SENT_WEEK, -1) == weekKey) return
        if (!PermissionManager.isNotificationGranted(context)) return

        val title = context.getString(R.string.weekly_report_title)
        val body = fetchReportText(context, repo)

        val openPi = PendingIntent.getActivity(
            context, NOTIF_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val shareSend = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n\n$body")
        }
        val sharePi = PendingIntent.getActivity(
            context, NOTIF_ID + 1,
            Intent.createChooser(shareSend, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return
        val n = NotificationCompat.Builder(context, GuardianApp.CHANNEL_GUARDIAN)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .addAction(0, context.getString(R.string.weekly_notif_action_share), sharePi)
            .setContentIntent(openPi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, n)
        prefs.edit().putInt(C_LAST_SENT_WEEK, weekKey).apply()
        Timber.i("WeeklyReporter: digest posted for week $weekKey")
    }
}
