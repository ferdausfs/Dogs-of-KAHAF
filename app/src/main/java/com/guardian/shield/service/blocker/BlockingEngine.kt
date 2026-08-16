package com.guardian.shield.service.blocker

import android.content.Context
import android.content.Intent
import com.guardian.shield.data.local.db.BlockEventDao
import com.guardian.shield.data.local.db.BlockEventEntity
import com.guardian.shield.data.local.datastore.GuardianPreferences
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.ui.overlay.BlockOverlayActivity
import com.guardian.shield.util.GuardianConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockEventDao: BlockEventDao,
    private val tempBlockManager: TempBlockManager,
    private val prefs: GuardianPreferences
) {
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val blockThrottleMap = LinkedHashMap<String, Long>()

    @Volatile private var cachedTempBlockMins: Int = 15

    init {
        ioScope.launch {
            try { prefs.tempBlockDurationMins.collect { cachedTempBlockMins = it } }
            catch (t: Throwable) { Timber.e(t) }
        }
    }

    /**
     * Count an AI strike without launching the overlay.
     * @return temp-block detail when the threshold is reached, otherwise null
     *         (silent strike or grace period — caller must NOT kick the user home).
     */
    fun evaluateAiStrike(pkg: String): String? {
        val durationMs = cachedTempBlockMins * 60 * 1_000L
        return when (val result = tempBlockManager.recordAiDetection(pkg, durationMs)) {
            is AiStrikeResult.Blocked -> result.detail
            AiStrikeResult.NoBlock,
            AiStrikeResult.GracePeriod -> null
        }
    }

    fun block(pkg: String, reason: BlockReason, detail: String) {
        val now = System.currentTimeMillis()
        val throttleMs = when (reason) {
            BlockReason.APP_BLOCKED,
            BlockReason.SCHEDULE_BLOCKED -> 500L
            // Increased throttle for AI to prevent double-logging during the overlay transition
            else -> GuardianConstants.BLOCK_THROTTLE_MS.coerceAtLeast(4000L)
        }
        synchronized(blockThrottleMap) {
            val last = blockThrottleMap[pkg] ?: 0L
            if (now - last < throttleMs) return
            blockThrottleMap[pkg] = now
            if (blockThrottleMap.size > GuardianConstants.MAX_THROTTLE_MAP) {
                val it = blockThrottleMap.entries.iterator()
                if (it.hasNext()) { it.next(); it.remove() }
            }
        }

        // If the accessibility service already consumed the strike (detail is
        // already a temp_block payload), do not count it a second time.
        val finalDetail = if (reason == BlockReason.AI_DETECTION &&
            !detail.startsWith("temp_block:")
        ) {
            evaluateAiStrike(pkg) ?: return
        } else detail

        launchOverlay(pkg, reason, finalDetail)
        logEvent(pkg, reason, finalDetail)
    }

    fun isTempBlocked(pkg: String): TempBlock? = tempBlockManager.isTempBlocked(pkg)

    /** True right after a temp block expires, when AI re-blocking is paused. */
    fun isGracePeriodActive(pkg: String): Boolean = tempBlockManager.isGracePeriodActive(pkg)

    private fun launchOverlay(pkg: String, reason: BlockReason, detail: String) {
        runCatching {
            context.startActivity(
                Intent(context, BlockOverlayActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(BlockOverlayActivity.EXTRA_PACKAGE, pkg)
                    putExtra(BlockOverlayActivity.EXTRA_REASON, reason.name)
                    putExtra(BlockOverlayActivity.EXTRA_DETAIL, detail)
                }
            )
        }
    }

    private fun logEvent(pkg: String, reason: BlockReason, detail: String) {
        ioScope.launch {
            runCatching {
                blockEventDao.insert(
                    BlockEventEntity(
                        packageName = pkg,
                        reason = reason.name,
                        matchedTerm = detail.ifBlank { null },
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}