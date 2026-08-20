package com.guardian.shield.accountability

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.guardian.shield.GuardianApp
import com.guardian.shield.R
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.data.local.db.BlockEventDao
import com.guardian.shield.domain.model.BlockReason
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE 2 (v3.5.0) — Accountability partner notifier.
 *
 * HONEST MECHANISM DISCLOSURE (what "notify" means here):
 * This app has no backend server and silently sending email/SMS from the
 * device requires the user's mail credentials — which must not be embedded
 * in an APK. So this implementation does NOT (and cannot) send anything by
 * itself. Instead, on each accountability event it posts a high-priority
 * local notification with two one-tap actions:
 *
 *   📧 EMAIL — opens the user's mail app pre-addressed to the partner with
 *      a pre-written subject + body (Intent.ACTION_SENDTO mailto:). The send
 *      is done by the user's own mail app under their identity; one tap is
 *      required. The partner genuinely receives the email once sent.
 *   📤 SHARE — the same pre-written text via the Android share sheet (any
 *      messaging app the user chooses).
 *
 * The weekly summary (Settings → Partner) builds a real activity digest from
 * BlockEventEntity rows + the durable tamper_log.txt and opens the same two
 * channels. Nothing is stored off-device; nothing is transmitted silently.
 *
 * Event sources (all observe-only):
 *   - protection pauses — GuardianPreferences.protectionEnabled true→false
 *   - tamper attempts   — AccountabilityEvents from TamperLogger
 *   - HIGH-confidence cooling-off reports — AccountabilityEvents from
 *     PendingReportManager.enqueue
 */
@Singleton
class AccountabilityNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: GuardianPreferences,
    private val blockEventDao: BlockEventDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** First emission of the prefs flow must not count as a "pause". */
    @Volatile private var lastProtectionEnabled: Boolean? = null
    @Volatile private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        AccountabilityEvents.setListener { event -> onEvent(event) }
        scope.launch {
            runCatching {
                prefs.protectionEnabled.collect { enabled ->
                    val last = lastProtectionEnabled
                    lastProtectionEnabled = enabled
                    if (last == true && !enabled) {
                        onEvent(
                            AccountabilityEvents.Event(
                                AccountabilityEvents.Kind.PROTECTION_PAUSED,
                                "protection_enabled=false"
                            )
                        )
                    }
                }
            }.onFailure { Timber.e(it, "Protection-flow observation failed") }
        }
        Timber.i("AccountabilityNotifier started")
    }

    private fun onEvent(event: AccountabilityEvents.Event) {
        scope.launch {
            runCatching {
                val email = prefs.partnerEmail.first()
                if (email.isBlank()) return@runCatching   // no partner configured
                val name = prefs.partnerName.first().ifBlank { email }
                val (titleKey, body) = when (event.kind) {
                    AccountabilityEvents.Kind.PROTECTION_PAUSED ->
                        R.string.partner_notif_pause_title to
                            context.getString(R.string.partner_notif_pause_body, name)
                    AccountabilityEvents.Kind.TAMPER_DETECTED ->
                        R.string.partner_notif_tamper_title to
                            context.getString(R.string.partner_notif_tamper_body, name, event.detail)
                    AccountabilityEvents.Kind.HIGH_CONFIDENCE_REPORT ->
                        R.string.partner_notif_report_title to
                            context.getString(R.string.partner_notif_report_body, name, event.detail)
                }
                postAlert(context.getString(titleKey), body, name, email, event)
            }.onFailure { Timber.w(it, "Accountability notification failed (${event.kind})") }
        }
    }

    // ------------------------------------------------------------------
    // Notification + one-tap sending actions
    // ------------------------------------------------------------------

    private fun postAlert(
        title: String,
        body: String,
        partnerName: String,
        partnerEmail: String,
        event: AccountabilityEvents.Event
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val emailIntent = buildEmailIntent(partnerEmail, title, body)
        val emailPi = PendingIntent.getActivity(
            context, event.kind.ordinal * 2,
            emailIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val sharePi = PendingIntent.getActivity(
            context, event.kind.ordinal * 2 + 1,
            Intent.createChooser(buildShareIntent(title, body), null),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, GuardianApp.CHANNEL_GUARDIAN)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .addAction(0, context.getString(R.string.partner_notif_action_email), emailPi)
            .addAction(0, context.getString(R.string.partner_notif_action_share), sharePi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID + event.kind.ordinal, n)
        Timber.w("Accountability alert posted: ${event.kind} -> $partnerEmail (tap-to-send)")
    }

    /** mailto: intent — pre-addressed and pre-written; the user taps Send. */
    fun buildEmailIntent(to: String, subject: String, body: String): Intent =
        Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Generic share-sheet intent with the same text. */
    fun buildShareIntent(subject: String, body: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    // ------------------------------------------------------------------
    // Weekly summary (Settings → send yourself)
    // ------------------------------------------------------------------

    /**
     * Build a real weekly digest from BlockEventEntity rows (this week vs last
     * week) plus the number of tamper attempts recorded in the durable
     * tamper_log.txt within the window. No detection data leaves the device.
     */
    suspend fun buildWeeklySummary(partnerName: String, partnerEmail: String): Pair<String, String> {
        val now = System.currentTimeMillis()
        val weekMs = 7L * 24 * 60 * 60 * 1_000L
        val thisWeekStart = now - weekMs
        val lastWeekStart = now - 2 * weekMs

        val events = runCatching { blockEventDao.eventsSince(lastWeekStart) }
            .getOrDefault(emptyList())
            .filter { it.reason != BlockReason.NOT_SENSITIVE.name }
        val thisWeek = events.count { it.timestamp >= thisWeekStart }
        val lastWeek = events.count { it.timestamp < thisWeekStart }
        val deltaPct = when {
            lastWeek == 0 -> null
            else -> ((thisWeek - lastWeek) * 100) / lastWeek
        }
        val topApp = events.filter { it.timestamp >= thisWeekStart }
            .groupingBy { it.packageName }.eachCount()
            .maxByOrNull { it.value }?.key
        val tamperThisWeek = countTamperAttemptsSince(thisWeekStart)

        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val subject = context.getString(
            R.string.partner_summary_subject, dateFmt.format(Date(now))
        )
        val body = buildString {
            append(context.getString(R.string.partner_summary_greeting, partnerName)).append('\n').append('\n')
            append("• ").append(context.getString(R.string.partner_summary_this_week_fmt, thisWeek)).append('\n')
            append("• ").append(context.getString(R.string.partner_summary_last_week_fmt, lastWeek)).append('\n')
            if (deltaPct != null) {
                val dir = context.getString(
                    if (deltaPct <= 0) R.string.partner_summary_less else R.string.partner_summary_more
                )
                append("• ").append(
                    context.getString(R.string.partner_summary_delta_fmt, kotlin.math.abs(deltaPct), dir)
                ).append('\n')
            }
            append("• ").append(context.getString(R.string.partner_summary_tamper_fmt, tamperThisWeek)).append('\n')
            if (topApp != null) {
                append("• ").append(context.getString(R.string.partner_summary_top_app_fmt, topApp)).append('\n')
            }
            append('\n').append(context.getString(R.string.partner_summary_footer))
        }
        return subject to body
    }

    /** Count lines in files/tamper_log.txt newer than [since]. Best-effort. */
    private fun countTamperAttemptsSince(since: Long): Int = runCatching {
        val f = File(context.filesDir, "tamper_log.txt")
        if (!f.exists()) return@runCatching 0
        f.readLines(Charsets.UTF_8).count { line ->
            line.substringBefore('|').toLongOrNull()?.let { it >= since } == true
        }
    }.getOrDefault(0)

    companion object {
        private const val NOTIF_ID = 1300
    }
}
