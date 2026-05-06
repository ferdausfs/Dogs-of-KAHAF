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

@Singleton
class BlockingEngine @Inject constructor(
    private val prefs: GuardianPreferences
) {
    companion object {
        private const val TAG = "Guardian_Blocker"
        private const val BLOCK_COOLDOWN_MS = 1_500L
        private const val DEFAULT_DELAY_SECS = 30
    }

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

    @MainThread
    fun executeBlock(
        service: AccessibilityService,
        pkg: String,
        appName: String,
        reason: BlockReason,
        detail: String = ""
    ) {
        val now = System.currentTimeMillis()
        val prev = lastBlockTime.get()

        if (now - prev < BLOCK_COOLDOWN_MS) {
            Timber.d("$TAG cooldown active, skipping block")
            return
        }

        if (!lastBlockTime.compareAndSet(prev, now)) {
            Timber.d("$TAG another thread won the block race")
            return
        }

        Timber.w("$TAG BLOCK pkg=$pkg reason=$reason detail=$detail")

        // CRITICAL FIX: Launch overlay FIRST, then go home.
        //
        // Old (buggy) order: HOME → startActivity(overlay)
        //   Problem: HOME is async. The blocked app can regain focus in the
        //   gap before our overlay launches — especially on slow devices.
        //
        // New (fixed) order: startActivity(overlay) → HOME
        //   Overlay is queued in the activity stack immediately. Then HOME
        //   sends the blocked app to background. Our overlay stays on top.

        // Step 1: Launch block UI immediately
        try {
            val intent = Intent(service, BlockOverlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                putExtra(BlockOverlayActivity.EXTRA_PKG, pkg)
                putExtra(BlockOverlayActivity.EXTRA_APP_NAME, appName)
                putExtra(BlockOverlayActivity.EXTRA_REASON, reason.name)
                putExtra(BlockOverlayActivity.EXTRA_DETAIL, detail)
                putExtra(BlockOverlayActivity.EXTRA_DELAY_SECS, cachedDelaySecs)
            }
            service.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "$TAG launch overlay failed")
        }

        // Step 2: Send blocked app to background AFTER overlay is queued
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            Timber.e(e, "$TAG GLOBAL_ACTION_HOME failed")
        }
    }

    fun resetCooldown() {
        lastBlockTime.set(0L)
    }
}