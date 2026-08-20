package com.guardian.shield.service.blocker

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.guardian.shield.data.local.db.PendingReportDao
import com.guardian.shield.service.detection.ConfirmedSensitiveMemory
import com.guardian.shield.service.detection.FalsePositiveMemory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * TASK B — WorkManager worker that fires when a pending report's cooling-off
 * delay expires. Reads the PENDING row, applies the deferred action, and marks
 * the row as APPLIED.
 *
 * For WARNING_CARD source (strike 1/2 "Not sensitive"):
 *   cancelLastStrike() + addSignature() (same as the instant LOW-conf path).
 *
 * For FULL_BLOCK source (strike 3 "Mark False"):
 *   clearTempBlock() + addSignature() + relaunch app (same as the instant LOW-conf path).
 *   Bug D's guarantee: the unblock runs unconditionally; the learning is best-effort.
 */
@HiltWorker
class ApplyPendingReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val pendingReportDao: PendingReportDao,
    private val tempBlockManager: TempBlockManager,
    private val falsePositiveMemory: FalsePositiveMemory,
    // v3.6.0 — a queued report is re-checked against the confirmed-sensitive
    // memory at APPLY time (defense in depth: the pattern may have been
    // protected between enqueue and expiry).
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

        // Take the pending candidate signature (best-effort learning).
        val sig = falsePositiveMemory.takePendingCandidate()

        // v3.6.0 — confirmed-sensitive refusal override at APPLY time (defense
        // in depth). The pattern may have been protected after this report was
        // enqueued; if so the deferred action is refused entirely — no strike
        // cancel, no temp-block clear, no addSignature, no relaunch. The row is
        // marked CANCELLED so the refusal is visible in Pending Reports.
        if (sig != null && confirmedSensitiveMemory.isConfirmedSignature(sig)) {
            Timber.w(
                "ApplyPendingReportWorker: report $reportId REFUSED — pattern became confirmed-sensitive (protected)"
            )
            pendingReportDao.updateStatus(reportId, PendingReportManager.Status.CANCELLED)
            return Result.success()
        }

        when (report.source) {
            PendingReportManager.Source.WARNING_CARD -> {
                // Strike 1/2 "Not sensitive" deferred action:
                // cancelLastStrike() + learn pattern.
                tempBlockManager.cancelLastStrike(report.packageName)
                if (sig != null) {
                    falsePositiveMemory.addSignature(sig)
                } else {
                    Timber.w("Pending WARNING_CARD: no pending candidate to learn for ${report.packageName}")
                }
            }
            PendingReportManager.Source.FULL_BLOCK -> {
                // Strike 3 "Mark False" deferred action:
                // clearTempBlock() + learn pattern + relaunch app.
                // Bug D preserved: clearTempBlock runs unconditionally.
                tempBlockManager.clearTempBlock(report.packageName)
                if (sig != null) {
                    falsePositiveMemory.addSignature(sig)
                } else {
                    Timber.w("Pending FULL_BLOCK: no pending candidate to learn for ${report.packageName}")
                }
                // Relaunch the blocked app.
                try {
                    val launchIntent = applicationContext.packageManager
                        .getLaunchIntentForPackage(report.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        applicationContext.startActivity(launchIntent)
                    } else {
                        Timber.w("Pending FULL_BLOCK: no launch intent for ${report.packageName}")
                    }
                } catch (t: Throwable) {
                    Timber.e(t, "Pending FULL_BLOCK: relaunch failed for ${report.packageName}")
                }
            }
            else -> {
                Timber.w("ApplyPendingReportWorker: unknown source ${report.source}")
            }
        }

        // Mark as applied.
        pendingReportDao.updateStatus(reportId, PendingReportManager.Status.APPLIED)
        return Result.success()
    }

    companion object {
        const val KEY_REPORT_ID = "report_id"
    }
}
