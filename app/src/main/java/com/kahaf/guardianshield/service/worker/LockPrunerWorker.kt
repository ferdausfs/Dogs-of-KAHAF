package com.kahaf.guardianshield.service.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kahaf.guardianshield.domain.repository.AppLockRepository
import com.kahaf.guardianshield.domain.repository.BlockEventRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class LockPrunerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val appLockRepository: AppLockRepository,
    private val blockEventRepository: BlockEventRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            appLockRepository.pruneExpired()
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            blockEventRepository.pruneOlderThan(cutoff)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
