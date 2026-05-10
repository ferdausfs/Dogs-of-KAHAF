package com.guardian.shield.domain.model

sealed class DetectionResult {
    object Allow : DetectionResult()
    data class Block(val reason: BlockReason, val detail: String? = null) : DetectionResult()
}
