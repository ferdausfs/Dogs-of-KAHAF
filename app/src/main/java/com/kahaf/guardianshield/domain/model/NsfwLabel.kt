package com.kahaf.guardianshield.domain.model

/** Tiered classifier output. Order matters (ordinal used for severity comparisons). */
enum class NsfwLabel {
    SAFE,
    NATURAL,
    SUGGESTIVE,
    EXPLICIT
}

data class NsfwResult(
    val label: NsfwLabel,
    val confidence: Float,
    val scores: Map<NsfwLabel, Float>
)
