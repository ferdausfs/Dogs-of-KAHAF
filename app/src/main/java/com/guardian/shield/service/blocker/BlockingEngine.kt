package com.guardian.shield.service.blocker

import android.content.Context
import android.content.Intent
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.repository.RulesRepository
import com.guardian.shield.ui.overlay.BlockOverlayActivity
import com.guardian.shield.util.GuardianConstants
import com.guardian.shield.util.Scopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: RulesRepository
) {
    private val throttle = LinkedHashMap<String, Long>()
    private val ioScope = Scopes.io()

    @Synchronized
    private fun shouldBlock(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        val last = throttle[pkg] ?: 0L
        if (now - last < GuardianConstants.BLOCK_THROTTLE_MS) return false
        throttle[pkg] = now
        if (throttle.size > GuardianConstants.MAX_THROTTLE_MAP) {
            val it = throttle.entries.iterator()
            if (it.hasNext()) { it.next(); it.remove() }
        }
        return true
    }

    fun block(pkg: String, reason: BlockReason, detail: String? = null) {
        if (!shouldBlock(pkg)) return
        Timber.i("Blocking %s reason=%s detail=%s", pkg, reason, detail)

        // 1) Launch HOME first
        runCatching {
            val home = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(home)
        }

        // 2) Launch BlockOverlayActivity
        runCatching {
            val overlay = Intent(context, BlockOverlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_HISTORY
                )
                putExtra(BlockOverlayActivity.EXTRA_PACKAGE, pkg)
                putExtra(BlockOverlayActivity.EXTRA_REASON, reason.name)
                putExtra(BlockOverlayActivity.EXTRA_DETAIL, detail)
            }
            context.startActivity(overlay)
        }

        // 3) Log event
        ioScope.launch {
            try {
                repo.logBlock(
                    BlockEvent(
                        id = 0,
                        packageName = pkg,
                        reason = reason,
                        matchedTerm = detail,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (t: Throwable) {
                Timber.e(t, "Failed to log block event")
            }
        }
    }
}
