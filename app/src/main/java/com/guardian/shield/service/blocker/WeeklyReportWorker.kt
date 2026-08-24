package com.guardian.shield.service.blocker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.guardian.shield.domain.repository.RulesRepository
import com.guardian.shield.util.WeeklyReporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * R7.6 — daily self-check for the weekly digest: posts once on Sunday (the
 * worker itself is deduped per ISO week inside [WeeklyReporter.maybeNotify]).
 */
@HiltWorker
class WeeklyReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: RulesRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            WeeklyReporter.maybeNotify(applicationContext, repo)
            Result.success()
        } catch (t: Throwable) {
            Timber.e(t, "WeeklyReportWorker failed")
            Result.retry()
        }
    }
}
