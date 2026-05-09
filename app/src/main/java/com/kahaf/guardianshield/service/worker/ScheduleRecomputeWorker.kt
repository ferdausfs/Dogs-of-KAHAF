package com.kahaf.guardianshield.service.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kahaf.guardianshield.service.timed.TimedBlockManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ScheduleRecomputeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val timedBlockManager: TimedBlockManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            timedBlockManager.recompute()
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
