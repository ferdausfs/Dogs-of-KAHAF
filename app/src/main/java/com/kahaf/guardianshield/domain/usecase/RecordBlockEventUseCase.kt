package com.kahaf.guardianshield.domain.usecase

import com.kahaf.guardianshield.domain.model.BlockReason
import com.kahaf.guardianshield.domain.repository.BlockEventRepository
import javax.inject.Inject

class RecordBlockEventUseCase @Inject constructor(
    private val blockEventRepository: BlockEventRepository
) {
    suspend operator fun invoke(packageName: String, reason: BlockReason, detail: String = "") {
        blockEventRepository.log(packageName, reason, detail)
    }
}
