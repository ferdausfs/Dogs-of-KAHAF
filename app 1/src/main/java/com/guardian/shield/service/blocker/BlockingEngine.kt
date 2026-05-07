package com.guardian.shield.service.blocker

import android.content.Context
import android.content.Intent
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import com.guardian.shield.domain.usecase.LogBlockEventUseCase
import com.guardian.shield.ui.overlay.BlockOverlayActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logEvent: LogBlockEventUseCase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastBlockMs = 0L

    fun block(packageName: String, reason: BlockReason, term: String? = null) {
        val now = System.currentTimeMillis()
        if (now - lastBlockMs < 800) return
        lastBlockMs = now

        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(home) }

        val overlay = Intent(context, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BlockOverlayActivity.EXTRA_PACKAGE, packageName)
            putExtra(BlockOverlayActivity.EXTRA_REASON, reason.name)
            putExtra(BlockOverlayActivity.EXTRA_TERM, term)
        }
        runCatching { context.startActivity(overlay) }

        scope.launch {
            logEvent(BlockEvent(packageName = packageName, reason = reason, matchedTerm = term))
        }
    }
}
