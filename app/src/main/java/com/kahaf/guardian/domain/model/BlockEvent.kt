package com.kahaf.guardian.domain.model

data class BlockEvent(
    val id: Long = 0,
    val packageName: String,
    val reason: BlockReason,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
