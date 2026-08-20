package com.guardian.shield.service.blocker

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.guardian.shield.data.local.db.PendingReportDao
import com.guardian.shield.data.local.db.PendingReportEntity
import com.guardian.shield.service.detection.ImageSignature
import com.guardian.shield.util.GuardianConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TASK B — Confidence-based cooling-off manager.
 *
 * When the AI detection confidence >= [GuardianConstants.CONFIDENCE_THRESHOLD]
 * the user's "Not sensitive" (strike 1/2) or "Mark False" (strike 3) report
 * is NOT applied immediately. Instead:
 *   1. A PENDING row is inserted into the pending_reports table.
 *   2. A WorkManager one-time worker is scheduled for scheduledApplyAt.
 *   3. The user sees honest feedback showing when the action will take effect.
 *   4. The user can cancel pending entries before they apply.
 *   5. The image signature is snapshotted into the row at enqueue time so
 *      the worker learns the ORIGINAL pattern (v3.6.1).
 *
 * Escalating delay (rolling 24-hour window per package):
 *   delay(n) = min(BASE * 2^(n-1), MAX)
 *   where n = 1-based count of HIGH-confidence reports in the window.
 *   1st → 2h, 2nd → 4h, 3rd → 8h, 4th → 16h, 5th+ → 24h (capped).
 */
@Singleton
class PendingReportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingReportDao: PendingReportDao
) {
    /** Source constants matching the entity column values. */
    object Source {
        const val WARNING_CARD = "WARNING_CARD"
        const val FULL_BLOCK = "FULL_BLOCK"
    }

    object Status {
        const val PENDING = "PENDING"
        const val APPLIED = "APPLIED"
        const val CANCELLED = "CANCELLED"
    }

    /**
     * Returns true if the confidence is HIGH (>= threshold) and the report
     * should be deferred. The caller branches on this: HIGH → queue, LOW → apply.
     */
    fun isHighConfidence(confidence: Float): Boolean =
        confidence >= GuardianConstants.CONFIDENCE_THRESHOLD

    /**
     * Compute the escalating delay for the n-th HIGH-confidence report in the
     * trailing 24-hour window for [pkg].
     *
     * Formula: delay(n) = min(BASE * 2^(n-1), MAX)
     *   n=1 → 2h, n=2 → 4h, n=3 → 8h, n=4 → 16h, n=5+ → 24h
     */
    fun computeDelayMs(countInWindow: Int): Long {
        // Cap n so `1L shl (n-1)` cannot overflow a Long (n=8 already
        // overshoots the 24h MAX: 2h * 2^7 = 256h).
        val n = countInWindow.coerceIn(1, 8)
        val multiplier = 1L shl (n - 1) // 2^(n-1)
        return (GuardianConstants.COOLING_BASE_DELAY_MS * multiplier)
            .coerceAtMost(GuardianConstants.COOLING_MAX_DELAY_MS)
    }

    /**
     * Queue a pending report. Returns the inserted entity's ID and the
     * computed delay/scheduled time so the caller can display feedback.
     *
     * @param pkg package that was blocked
     * @param confidence the AI confidence score (>= threshold)
     * @param source WARNING_CARD or FULL_BLOCK
     * @param strikeCount the strike count at report time
     * @param signature the image signature captured at report time (may be
     *        null if the in-memory candidate did not survive). Persisted so
     *        the worker can learn THIS pattern hours later instead of
     *        whatever `pendingCandidate` happens to hold then.
     */
    suspend fun enqueue(
        pkg: String,
        confidence: Float,
        source: String,
        strikeCount: Int,
        signature: IntArray? = null
    ): EnqueueResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val windowStart = now - GuardianConstants.COOLING_WINDOW_MS
        val countInWindow = pendingReportDao.countHighConfSince(pkg, windowStart)
        // countInWindow is the number of existing rows; the new one will be
        // the (countInWindow+1)-th in the window.
        val delayMs = computeDelayMs(countInWindow + 1)
        val scheduledAt = now + delayMs

        val entity = PendingReportEntity(
            packageName = pkg,
            timestampCreated = now,
            scheduledApplyAt = scheduledAt,
            confidence = confidence,
            source = source,
            status = Status.PENDING,
            strikeCount = strikeCount,
            delayMs = delayMs,
            signatureCsv = signature?.let { ImageSignature.toCsv(it) }.orEmpty()
        )
        val id = pendingReportDao.insert(entity)
        Timber.i(
            "PendingReport queued: id=$id pkg=$pkg conf=$confidence src=$source " +
                "delay=${delayMs / 60_000}min applyAt=$scheduledAt"
        )

        // Schedule the WorkManager worker.
        scheduleApplyWork(id, scheduledAt)

        // PHASE 2 (v3.5.0) — observe-only accountability hook. The row is
        // already inserted and the worker scheduled above; this emit comes
        // strictly AFTER the cooling-off decision and cannot change it
        // (emit swallows listener errors).
        runCatching {
            com.guardian.shield.accountability.AccountabilityEvents.emit(
                com.guardian.shield.accountability.AccountabilityEvents.Kind.HIGH_CONFIDENCE_REPORT,
                "pkg=$pkg conf=${"%.2f".format(confidence)} src=$source delayMin=${delayMs / 60_000}"
            )
        }

        EnqueueResult(id = id, delayMs = delayMs, scheduledApplyAt = scheduledAt)
    }

    /**
     * Cancel a pending report before it applies.
     * @return true if a PENDING row was found and cancelled.
     */
    suspend fun cancel(id: Long): Boolean = withContext(Dispatchers.IO) {
        val entity = pendingReportDao.getById(id) ?: return@withContext false
        if (entity.status != Status.PENDING) return@withContext false
        pendingReportDao.cancel(id)
        // Cancel the scheduled WorkManager work.
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        Timber.i("PendingReport cancelled: id=$id pkg=${entity.packageName}")
        true
    }

    /**
     * Mark a pending report as APPLIED (called by the worker when the delay expires).
     */
    suspend fun markApplied(id: Long) = withContext(Dispatchers.IO) {
        pendingReportDao.updateStatus(id, Status.APPLIED)
        Timber.i("PendingReport applied: id=$id")
    }

    suspend fun getPending() = pendingReportDao.getPending()

    fun observePending() = pendingReportDao.observePending()

    // --- private helpers ---

    private fun workName(id: Long) = "pending_report_apply_$id"

    private fun scheduleApplyWork(id: Long, scheduledAt: Long) {
        val delayMs = (scheduledAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val data = Data.Builder()
            .putLong(ApplyPendingReportWorker.KEY_REPORT_ID, id)
            .build()
        val request = OneTimeWorkRequestBuilder<ApplyPendingReportWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .setConstraints(Constraints.Builder().build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    data class EnqueueResult(
        val id: Long,
        val delayMs: Long,
        val scheduledApplyAt: Long
    )
}
