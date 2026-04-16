package com.kahaf.guardian.domain.usecase

import com.kahaf.guardian.domain.model.BlockEvent
import com.kahaf.guardian.domain.repository.BlockLogRepository
import javax.inject.Inject

class LogBlockEventUseCase @Inject constructor(
    private val blockLogRepository: BlockLogRepository
) {
    suspend operator fun invoke(event: BlockEvent) {
        blockLogRepository.logBlockEvent(event)
    }
}