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
 * FIX-LOG (vs original):
 *  - BUG #7 / #12: launching the BlockOverlayActivity from background on
 *    Android 10+ (and especially MIUI / ColorOS / FunTouchOS) is unreliable.
 *    We now (a) ALWAYS go HOME first to evict the offending app from the
 *    foreground, and (b) launch the overlay with ActivityOptions / a clean
 *    new task — which is the documented escape hatch for accessibility
 *    services to start an Activity from background.
 *  - Catch + log every Intent dispatch so a failure on one OEM does not
 *    silently break the entire block path.
 */
@Singleton
class BlockingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logEvent: LogBlockEventUseCase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastBlockMs = 0L
    private var lastBlockedPkg: String? = null

    fun block(packageName: String, reason: BlockReason, term: String? = null) {
        val now = System.currentTimeMillis()
        // De-dupe rapid repeats but allow re-blocking when the offending
        // package changes (was: bare 800ms global throttle).
        if (packageName == lastBlockedPkg && now - lastBlockMs < 800) return
        lastBlockMs = now
        lastBlockedPkg = packageName

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
