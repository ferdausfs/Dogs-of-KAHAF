package com.kahaf.guardianshield.domain.usecase

import com.kahaf.guardianshield.domain.model.AppRuleState
import com.kahaf.guardianshield.domain.model.BlockReason
import com.kahaf.guardianshield.domain.repository.AppLockRepository
import com.kahaf.guardianshield.domain.repository.AppRuleRepository
import com.kahaf.guardianshield.service.timed.TimedBlockManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decision engine: given the package that just came to the foreground,
 * decide whether the BlockOverlay should be shown — and why.
 *
 * Order of precedence (first match wins):
 *  1. Whitelisted     → never block
 *  2. App rule BLOCKED
 *  3. Active 15-minute lock
 *  4. Active schedule window
 */
data class EvalDecision(
    val shouldBlock: Boolean,
    val reason: BlockReason? = null,
    val detail: String = "",
    val lockedUntilMs: Long = 0L
)

@Singleton
class EvaluateForegroundAppUseCase @Inject constructor(
    private val appRuleRepository: AppRuleRepository,
    private val appLockRepository: AppLockRepository,
    private val timedBlockManager: TimedBlockManager
) {
    suspend operator fun invoke(packageName: String): EvalDecision {
        val state = appRuleRepository.getState(packageName)
        if (state == AppRuleState.WHITELISTED) return EvalDecision(false)
        if (state == AppRuleState.BLOCKED) {
            return EvalDecision(true, BlockReason.APP_RULE, "Blocked by app list")
        }
        val now = System.currentTimeMillis()
        appLockRepository.isLocked(packageName, now)?.let { lock ->
            return EvalDecision(
                shouldBlock = true,
                reason = BlockReason.AUTO_LOCK,
                detail = lock.reason,
                lockedUntilMs = lock.lockedUntilEpochMs
            )
        }
        if (timedBlockManager.isBlockedNow(packageName)) {
            return EvalDecision(
                shouldBlock = true,
                reason = BlockReason.SCHEDULE,
                detail = "Inside scheduled window"
            )
        }
        return EvalDecision(false)
    }
}
