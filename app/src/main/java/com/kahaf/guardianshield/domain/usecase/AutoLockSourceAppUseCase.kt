package com.kahaf.guardianshield.domain.usecase

import com.kahaf.guardianshield.domain.model.AiSettings
import com.kahaf.guardianshield.domain.repository.AppLockRepository
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * If the package is in the configured "content-source" set, lock it for 15 minutes.
 * The lock-until timestamp is persisted in Room so it survives process death.
 */
class AutoLockSourceAppUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLockRepository: AppLockRepository
) {
    suspend operator fun invoke(packageName: String, reason: String): Boolean {
        val ai: AiSettings = settingsRepository.aiSettings.first()
        if (packageName !in ai.contentSourcePackages) return false
        appLockRepository.lockFor(
            pkg = packageName,
            durationMs = LOCK_DURATION_MS,
            reason = reason
        )
        return true
    }

    companion object {
        const val LOCK_DURATION_MS: Long = 15L * 60L * 1000L
    }
}
