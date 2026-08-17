package com.guardian.shield.ui.overlay

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityBlockedDetailBinding
import com.guardian.shield.ui.activitylog.ActivityLogActivity
import com.guardian.shield.ui.settings.AppListActivity

/**
 * Optional expanded Blocked Content details screen per reference (b).
 * Entry point: ActivityLog row tap or BlockOverlay secondary action.
 * Flagged for approval — does not add new detection logic.
 *
 * Keeps Guardian Shield name & shield icon, no dog mascot.
 * Data comes from BlockOverlay extras or BlockEvent.
 */
class BlockedDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty().ifBlank { "Adult Content" }
        val source = intent.getStringExtra(EXTRA_SOURCE).orEmpty().ifBlank { "facebook.com/reel/..." }
        val time = intent.getStringExtra(EXTRA_TIME).orEmpty().ifBlank { "Today, 9:41 AM" }

        binding.txtApp.text = if (pkg.isNotBlank()) pkg else "Facebook • com.facebook.katana"
        binding.txtCategory.text = category
        binding.txtSource.text = source
        binding.txtTime.text = time

        binding.btnBackToApp.setOnClickListener {
            goHome()
        }

        binding.rowStayFocused.setOnClickListener {
            goHome()
        }

        binding.rowViewActivity.setOnClickListener {
            startActivity(Intent(this, ActivityLogActivity::class.java))
        }

        binding.rowWhitelist.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }

        binding.btnReport.setOnClickListener {
            // Existing false-positive flow could be triggered here if needed
            finish()
        }
    }

    private fun goHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_TIME = "extra_time"
    }
}
