package com.guardian.shield.domain.model

data class AppRule(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val isWhitelisted: Boolean,
    val createdAt: Long,
    // R12 (v3.8.2) — wall-clock time the block was set, driving the 3-minute
    // undo grace window. 0 = not blocked / legacy block (permanent, no grace).
    val blockedAtMs: Long = 0
) {
    companion object {
        /** R12 — an accidental block can be undone within 3 minutes (even
         *  while a Time-Lock is active); afterwards it is permanent. */
        const val BLOCK_GRACE_MS: Long = 3L * 60 * 1000L

        /** true while [blockedAtMs] sits inside the grace window. A future
         *  stamp (clock skew backwards) counts as within grace on purpose. */
        fun isWithinGrace(blockedAtMs: Long, now: Long = System.currentTimeMillis()): Boolean =
            blockedAtMs > 0 && (now - blockedAtMs) <= BLOCK_GRACE_MS

        fun graceRemainingMs(blockedAtMs: Long, now: Long = System.currentTimeMillis()): Long =
            if (blockedAtMs <= 0) 0L else (BLOCK_GRACE_MS - (now - blockedAtMs)).coerceAtLeast(0L)
    }
}
