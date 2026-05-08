package com.guardian.shield.service.blocker

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.usecase.LogBlockEventUseCase
import com.guardian.shield.ui.overlay.BlockOverlayActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v8 FIX-LOG (stability pass):
 *  • BUG-10 → de-dupe throttle is now a per-package map. Previously, a
 *    single (pkg, ts) pair was tracked, so rapid alternation between two
 *    blocked packages bypassed the throttle entirely → overlay launched
 *    5–10 times/sec. Capped at MAX_THROTTLE_MAP entries with oldest-out
 *    eviction (same pattern as GuardianAccessibilityService).
 *
 *  Existing behavior preserved:
 *   - HOME → overlay → log order is unchanged (must NEVER reorder).
 *   - Each Intent dispatch is wrapped in runCatching for OEM resilience.
 */
@Singleton
class BlockingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logEvent: LogBlockEventUseCase
) {
    companion object {
        private const val THROTTLE_MS = 800L
        private const val MAX_THROTTLE_MAP = 50
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // BUG-10: per-package timestamp map.
    private val lastBlockByPkg = HashMap<String, Long>()

    fun block(packageName: String, reason: BlockReason, term: String? = null) {
        val now = System.currentTimeMillis()

        // Per-package throttle.
        synchronized(lastBlockByPkg) {
            val last = lastBlockByPkg[packageName] ?: 0L
            if (now - last < THROTTLE_MS) return
            // Evict oldest if cap reached and this is a new key.
            if (lastBlockByPkg.size >= MAX_THROTTLE_MAP &&
                !lastBlockByPkg.containsKey(packageName)
            ) {
                val oldestKey = lastBlockByPkg.minByOrNull { it.value }?.key
                if (oldestKey != null) lastBlockByPkg.remove(oldestKey)
            }
            lastBlockByPkg[packageName] = now
        }

        // 1. Evict the offending app from the foreground.
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(home)
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
            val opts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }.toBundle()
            } else null
            context.startActivity(overlay, opts)
        }.onFailure { Timber.e(it, "Failed to launch BlockOverlayActivity") }

        // 3. Log the event.
        scope.launch {
            runCatching {
                logEvent(BlockEvent(packageName = packageName, reason = reason, matchedTerm = term))
            }.onFailure { Timber.w(it, "Failed to log block event") }
        }
    }
}
