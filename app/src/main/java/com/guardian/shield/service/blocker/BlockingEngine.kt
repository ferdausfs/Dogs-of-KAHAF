package com.guardian.shield.service.blocker

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.usecase.LogBlockEventUseCase
import com.guardian.shield.ui.overlay.BlockOverlayActivity
import com.guardian.shield.util.GuardianConstants
import com.guardian.shield.util.Scopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v11 (2.1.1) STABILITY PATCH:
 *  • CRITICAL FIX: HOME launch now also gets ActivityOptions on Android
 *    14+ (UPSIDE_DOWN_CAKE) with MODE_BACKGROUND_ACTIVITY_START_ALLOWED.
 *    Previously only the overlay had it; HOME would silently fail on
 *    some Android 14 OEM builds (Samsung One UI 6, Pixel) so the user
 *    stayed inside the offending app.
 *  • DEFENSIVE: getActivityOptions extracted into a helper.
 */
@Singleton
class BlockingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logEvent: LogBlockEventUseCase
) {
    companion object {
        private const val THROTTLE_MS = GuardianConstants.BLOCK_THROTTLE_MS
        private const val MAX_THROTTLE_MAP = GuardianConstants.MAX_THROTTLE_MAP
    }

    private val scope: CoroutineScope = Scopes.io()
    private val lastBlockByPkg = HashMap<String, Long>()

    fun block(packageName: String, reason: BlockReason, term: String? = null) {
        val now = System.currentTimeMillis()

        // Per-package throttle.
        synchronized(lastBlockByPkg) {
            val last = lastBlockByPkg[packageName] ?: 0L
            if (now - last < THROTTLE_MS) return
            if (lastBlockByPkg.size >= MAX_THROTTLE_MAP &&
                !lastBlockByPkg.containsKey(packageName)
            ) {
                val oldestKey = lastBlockByPkg.minByOrNull { it.value }?.key
                if (oldestKey != null) lastBlockByPkg.remove(oldestKey)
            }
            lastBlockByPkg[packageName] = now
        }

        val activityOpts = backgroundActivityOptions()

        // 1. Evict the offending app from the foreground.
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            // v11: pass ActivityOptions on Android 14+ so the launch is
            // not rejected as a background activity start.
            if (activityOpts != null) {
                context.startActivity(home, activityOpts)
            } else {
                context.startActivity(home)
            }
        }.onFailure { Timber.w(it, "Failed to launch HOME") }

        // 2. Show the full-screen block overlay.
        runCatching {
            val overlay = Intent(context, BlockOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_HISTORY
                putExtra(BlockOverlayActivity.EXTRA_PACKAGE, packageName)
                putExtra(BlockOverlayActivity.EXTRA_REASON, reason.name)
                putExtra(BlockOverlayActivity.EXTRA_TERM, term)
            }
            if (activityOpts != null) {
                context.startActivity(overlay, activityOpts)
            } else {
                context.startActivity(overlay)
            }
        }.onFailure { Timber.e(it, "Failed to launch BlockOverlayActivity") }

        // 3. Log the event.
        scope.launch {
            runCatching {
                logEvent(BlockEvent(packageName = packageName, reason = reason, matchedTerm = term))
            }.onFailure { Timber.w(it, "Failed to log block event") }
        }
    }

    /**
     * v11: build ActivityOptions for background activity launches on
     * Android 14+. Returns null on older versions (no options needed).
     */
    private fun backgroundActivityOptions(): android.os.Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return runCatching {
            ActivityOptions.makeBasic().apply {
                pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }.toBundle()
        }.getOrNull()
    }
}
