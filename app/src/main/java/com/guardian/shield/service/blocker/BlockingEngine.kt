package com.guardian.shield.service.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.ui.overlay.BlockOverlayActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BlockingEngine — executes blocking actions.
 *
 * Called by AccessibilityService when RulesEngine returns Block.
 * Responsibilities:
 *   1. Press HOME (remove blocked app from foreground)
 *   2. Launch BlockOverlayActivity (full-screen block UI)
 *   3. Enforce cooldown (prevent rapid re-triggering)
 */
@Singleton
class BlockingEngine @Inject constructor(
    private val prefs: GuardianPreferences
) {

    companion object {
        private const val TAG = "Guardian_Blocker"
        private const val BLOCK_COOLDOWN_MS = 3_000L
    }

    @Volatile private var lastBlockTime = 0L

    fun isCoolingDown(): Boolean =
        System.currentTimeMillis() - lastBlockTime < BLOCK_COOLDOWN_MS

    /**
     * Execute block: HOME + show overlay.
     */
    fun executeBlock(
        service: AccessibilityService,
        pkg: String,
        appName: String,
        reason: BlockReason,
        detail: String = ""
    ) {
        if (isCoolingDown()) {
            Timber.d("$TAG cooldown active — skip block for $pkg")
            return
        }
        lastBlockTime = System.currentTimeMillis()

        Timber.w("$TAG BLOCKING: pkg=$pkg reason=$reason detail=$detail")

        // Step 1: Push app to background
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

        // Step 2: Read delay setting (safe runBlocking — short prefs read)
        val delaySecs = runBlocking {
            try { prefs.delayUnlockSeconds.first() } catch (_: Exception) { 30 }
        }

        // Step 3: Show full-screen block overlay
        try {
            val intent = Intent(service, BlockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(BlockOverlayActivity.EXTRA_PKG,          pkg)
                putExtra(BlockOverlayActivity.EXTRA_APP_NAME,     appName)
                putExtra(BlockOverlayActivity.EXTRA_REASON,       reason.name)
                putExtra(BlockOverlayActivity.EXTRA_DETAIL,       detail)
                putExtra(BlockOverlayActivity.EXTRA_DELAY_SECS,   delaySecs)
            }
            service.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "$TAG failed to launch BlockOverlayActivity")
        }
    }

    fun resetCooldown() {
        lastBlockTime = 0L
    }
}
