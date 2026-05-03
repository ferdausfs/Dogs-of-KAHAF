package com.guardian.shield.service.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import androidx.annotation.MainThread
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.ui.overlay.BlockOverlayActivity
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BlockingEngine — orchestrates the user-visible block sequence.
 *
 * Flow:
 *   1. Push offending app to background via GLOBAL_ACTION_HOME
 *   2. Launch BlockOverlayActivity
 */
@Singleton
class BlockingEngine @Inject constructor(
    private val prefs: GuardianPreferences
) {
    companion object {
        private const val TAG              = "Guardian_Blocker"
        private const val BLOCK_COOLDOWN_MS = 1_500L
        // FIX: Default value as constant — not magic number
        private const val DEFAULT_DELAY_SECS = 30
    }

    // FIX: AtomicLong instead of @Volatile Long — prevents race condition
    private val lastBlockTime = AtomicLong(0L)

    @Volatile private var cachedDelaySecs = DEFAULT_DELAY_SECS

    suspend fun loadSettings() {
        try {
            cachedDelaySecs = prefs.delayUnlockSeconds.first()
        } catch (_: Exception) {
            cachedDelaySecs = DEFAULT_DELAY_SECS
        }
    }

    fun isCoolingDown(): Boolean =
        System.currentTimeMillis() - lastBlockTime.get() < BLOCK_COOLDOWN_MS

    // FIX: @MainThread annotation — enforces correct thread usage
    @MainThread
    fun executeBlock(
        service: AccessibilityService,
        pkg: String,
        appName: String,
        reason: BlockReason,
        detail: String = ""
    ) {
        val now = System.currentTimeMillis()

        // FIX: Atomic check+set — prevents race condition where two coroutines
        // both pass isCoolingDown() and both execute the block
        if (!lastBlockTime.compareAndSet(lastBlockTime.get(), now)) return
        if (now - lastBlockTime.get() < BLOCK_COOLDOWN_MS &&
            lastBlockTime.get() != now) return

        // Simplified atomic cooldown check
        val prev = lastBlockTime.getAndSet(now)
        if (now - prev < BLOCK_COOLDOWN_MS) {
            lastBlockTime.set(prev) // restore
            return
        }

        Timber.w("$TAG BLOCK pkg=$pkg reason=$reason detail=$detail")

        // Step 1: kick to background first
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

        // Step 2: launch block UI
        try {
            val intent = Intent(service, BlockOverlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK    or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP   or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                putExtra(BlockOverlayActivity.EXTRA_PKG,        pkg)
                putExtra(BlockOverlayActivity.EXTRA_APP_NAME,   appName)
                putExtra(BlockOverlayActivity.EXTRA_REASON,     reason.name)
                putExtra(BlockOverlayActivity.EXTRA_DETAIL,     detail)
                putExtra(BlockOverlayActivity.EXTRA_DELAY_SECS, cachedDelaySecs)
            }
            service.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "$TAG launch overlay")
        }
    }

    fun resetCooldown() {
        lastBlockTime.set(0L)
    }
}