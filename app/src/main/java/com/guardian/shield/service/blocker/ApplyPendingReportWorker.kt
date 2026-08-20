package com.guardian.shield.service.blocker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.guardian.shield.GuardianApp
import com.guardian.shield.R
import com.guardian.shield.data.local.db.PendingReportDao
import com.guardian.shield.service.detection.ConfirmedSensitiveMemory
import com.guardian.shield.service.detection.FalsePositiveMemory
import com.guardian.shield.service.detection.ImageSignature
import com.guardian.shield.ui.dashboard.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * TASK B — WorkManager worker that fires when a pending report's cooling-off
 * delay expires. Reads the PENDING row, applies the deferred action, and marks
 * the row as APPLIED.
 *
 * v3.6.1 contract (the original apply path was broken):
 *
 *  - Learning uses the signature SNAPSHOTTED at enqueue time
 *    ([PendingReportEntity.signatureCsv]). It never calls
 *    [FalsePositiveMemory.takePendingCandidate] — that in-memory slot is
 *    overwritten by later detections and is empty after process death, so
 *    reading it hours later would learn the WRONG pattern (or steal the
 *    candidate from a currently-showing warning card).
 *
 *  - WARNING_CARD does NOT call [TempBlockManager.cancelLastStrike]. The
 *    original strike expired via STRIKE_RESET_MS (10 min) long before a 2–24 h
 *    delay elapses; decrementing the live counter would undo a later,
 *    unrelated strike. The delayed action that still makes sense is learning
 *    the stored pattern so the same content stops re-triggering.
 *
 *  - FULL_BLOCK still calls [TempBlockManager.clearTempBlock] (needed when a
 *    24 h escalation is still running). It does NOT start an Activity from
 *    the worker — Android 10+ blocks background activity launches, and
 *    surprise-launching Instagram hours later is wrong even when allowed.
 *    A notification tells the user the report applied.
 */
@HiltWorker
class ApplyPendingReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val pendingReportDao: PendingReportDao,
    private val tempBlockManager: TempBlockManager,
    private val falsePositiveMemory: FalsePositiveMemory,
    private val confirmedSensitiveMemory: ConfirmedSensitiveMemory
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reportId = inputData.getLong(KEY_REPORT_ID, -1L)
        if (reportId < 0) {
            Timber.w("ApplyPendingReportWorker: missing report ID")
            return Result.success()
        }

        val report = pendingReportDao.getById(reportId)
        if (report == null) {
            Timber.w("ApplyPendingReportWorker: report $reportId not found (deleted?)")
            return Result.success()
        }

        if (report.status != PendingReportManager.Status.PENDING) {
            Timber.d("ApplyPendingReportWorker: report $reportId already ${report.status} — skipping")
            return Result.success()
        }

        Timber.i(
            "ApplyPendingReportWorker: applying deferred action for report $reportId " +
                "pkg=${report.packageName} src=${report.source} conf=${report.confidence}"
        )

        // v3.6.1 — use the signature persisted at enqueue time. Do NOT touch
        // the live pendingCandidate (that is the currently-showing detection,
        // if any — stealing it would break Protect / Not-sensitive on a new card).
        val sig = ImageSignature.fromCsv(report.signatureCsv)

        // Confirmed-sensitive refusal at APPLY time (defense in depth): the
        // pattern may have been protected after this report was enqueued.
        if (sig != null && confirmedSensitiveMemory.isConfirmedSignature(sig)) {
            Timber.w(
                "ApplyPendingReportWorker: report $reportId REFUSED — pattern became confirmed-sensitive (protected)"
            )
            pendingReportDao.updateStatus(reportId, PendingReportManager.Status.CANCELLED)
            return Result.success()
        }

        when (report.source) {
            PendingReportManager.Source.WARNING_CARD -> {
                // Strike 1/2 "Not sensitive" deferred action: learn the
                // ORIGINAL pattern. Do not cancelLastStrike — that counter is
                // a different (or already-reset) strike by the time we fire.
                if (sig != null) {
                    falsePositiveMemory.addSignature(sig)
                } else {
                    Timber.w("Pending WARNING_CARD: no stored signature to learn for ${report.packageName}")
                }
            }
            PendingReportManager.Source.FULL_BLOCK -> {
                // Strike 3 "Mark False" deferred action:
                // clearTempBlock (no-op if the 15 min block already expired;
                // required if a 24 h escalation is still running) + learn.
                // Bug D preserved: clearTempBlock runs unconditionally.
                tempBlockManager.clearTempBlock(report.packageName)
                if (sig != null) {
                    falsePositiveMemory.addSignature(sig)
                } else {
                    Timber.w("Pending FULL_BLOCK: no stored signature to learn for ${report.packageName}")
                }
            }
            else -> {
                Timber.w("ApplyPendingReportWorker: unknown source ${report.source}")
            }
        }

        pendingReportDao.updateStatus(reportId, PendingReportManager.Status.APPLIED)
        notifyApplied(report.packageName, report.source)
        return Result.success()
    }

    /**
     * Tell the user the cooling-off delay elapsed and the report applied.
     * Used instead of launching the blocked app from the background (BAL
     * restrictions on API 29+ make that start fail, and a surprise launch
     * hours later is the wrong UX even when it succeeds).
     */
    private fun notifyApplied(pkg: String, source: String) {
        runCatching {
            val pi = PendingIntent.getActivity(
                applicationContext, NOTIF_ID,
                Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val text = applicationContext.getString(R.string.cooling_applied_notif_text, pkg)
            val n = NotificationCompat.Builder(applicationContext, GuardianApp.CHANNEL_GUARDIAN)
                .setSmallIcon(R.drawable.ic_flag)
                .setContentTitle(applicationContext.getString(R.string.cooling_applied_notif_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as? android.app.NotificationManager
            nm?.notify(NOTIF_ID + (source.hashCode() and 0xFF), n)
        }.onFailure { Timber.w(it, "Failed to notify cooling-off apply for $pkg") }
    }

    companion object {
        const val KEY_REPORT_ID = "report_id"
        private const val NOTIF_ID = 9200
    }
}
