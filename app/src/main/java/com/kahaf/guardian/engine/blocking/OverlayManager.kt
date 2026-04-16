package com.kahaf.guardian.engine.blocking

import android.content.Context
import android.content.Intent
import com.kahaf.guardian.domain.model.DetectionResult
import com.kahaf.guardian.ui.block.BlockScreenActivity
import com.kahaf.guardian.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayManager @Inject constructor(private val ctx: Context) {
    fun showBlockScreen(result: DetectionResult) {
        ctx.startActivity(Intent(ctx, BlockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(Constants.EXTRA_BLOCK_REASON, result.reason?.displayName ?: "Blocked")
            putExtra(Constants.EXTRA_BLOCKED_PACKAGE, result.packageName)
        })
    }
}
