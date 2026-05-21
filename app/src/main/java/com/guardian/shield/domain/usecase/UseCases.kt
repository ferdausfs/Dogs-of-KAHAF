package com.guardian.shield.domain.usecase

import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.repository.RulesRepository
import javax.inject.Inject

class LogBlockUseCase @Inject constructor(
    private val repo: RulesRepository
) {
    suspend operator fun invoke(pkg: String, reason: BlockReason, matched: String? = null) {
        repo.logBlock(
            BlockEvent(
                id = 0,
                packageName = pkg,
                reason = reason,
                matchedTerm = matched,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
