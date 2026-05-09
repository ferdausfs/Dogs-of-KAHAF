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
 * v12 (2.1.2):
 *  • Use shared Scopes.appIo (app-lifetime singleton scope) instead of
 *    creating a new scope at construction. Singleton-scoped engine + new
 *    scope was effectively the same lifetime, but appIo is shared with
 *    other singletons and cannot be accidentally cancelled.
 *  • backgroundActivityOptions() result cached after first computation
 *    (the Build.VERSION check + ActivityOptions creation are not free).
 *
 * v11 (2.1.1):
 *  • HOME launch also gets ActivityOptions on Android 14+.
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

    private val scope: CoroutineScope = Scopes.appIo
    private val lastBlockByPkg = HashMap<String, Long>()

    @Volatile private var cachedActivityOptionsBundle: android.os.Bundle? = null
    @Volatile private var activityOptionsBuilt: Boolean = false

    fun block(packageName: String, reason: BlockReason, term: String? = null) {
        val now = System.currentTimeMillis()

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

        runCatching {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (activityOpts != null) {
                context.startActivity(home, activityOpts)
            } else {
                context.startActivity(home)
            }
        }.onFailure { Timber.w(it, "Failed to launch HOME") }

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

        scope.launch {
            runCatching {
                logEvent(BlockEvent(packageName = packageName, reason = reason, matchedTerm = term))
            }.onFailure { Timber.w(it, "Failed to log block event") }
        }
    }

    /**
     * v12: cached after first computation. Building the Bundle on every
     * block() is unnecessary — it never changes for the lifetime of the
     * process.
     */
    private fun backgroundActivityOptions(): android.os.Bundle? {
        if (activityOptionsBuilt) return cachedActivityOptionsBundle
        val bundle = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            null
        } else {
            runCatching {
                ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }.toBundle()
            }.getOrNull()
        }
        cachedActivityOptionsBundle = bundle
        activityOptionsBuilt = true
        return bundle
    }
}
