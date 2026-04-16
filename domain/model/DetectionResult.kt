package com.kahaf.guardian.domain.model

data class DetectionResult(
    val shouldBlock: Boolean,
    val reason: BlockReason? = null,
    val details: String = "",
    val packageName: String = "",
    val confidence: Float = 0f
)