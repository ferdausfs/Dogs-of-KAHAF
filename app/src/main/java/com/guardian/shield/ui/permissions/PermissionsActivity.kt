package com.guardian.shield.ui.permissions

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.guardian.shield.databinding.ActivityPermissionsBinding
import com.guardian.shield.databinding.ItemPermissionBinding
import com.guardian.shield.service.detection.PinManager
import com.guardian.shield.ui.setup.PinVerifyActivity
import com.guardian.shield.util.PermissionManager
import com.guardian.shield.util.PermissionManager.PermissionKey
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * One-stop "Permission Health" screen.
 *
 *  WHY:
 *    The user reported "permission auto remove hoy / sob thik ase kintu app
 *    kaj kore na" — they had no easy way to see WHICH permission was lost
 *    and re-grant it in one tap. This screen lists every permission Guardian
 *    needs, shows live status, and one-tap navigates to the right OS page.
 *
 *  Existing flows are NOT touched — this is a brand-new optional screen.
 */
@AndroidEntryPoint
class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding
    @Inject lateinit var pinManager: PinManager

    private val pinVerify = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) {
            binding.root.visibility = View.VISIBLE
            render()
        } else {
            finish()
        }
    }

    private val systemSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.visibility = View.INVISIBLE

        if (pinManager.isPinSet()) {
            pinVerify.launch(Intent(this, PinVerifyActivity::class.java))
        } else {
            binding.root.visibility = View.VISIBLE
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        if (binding.root.visibility == View.VISIBLE) render()
    }

    private fun render() {
        binding.container.removeAllViews()
        val statuses = PermissionManager.snapshot(this)
        val missingCritical = statuses.count { it.critical && !it.granted }

        binding.tvHeadline.text = if (missingCritical == 0) {
            "✓ All critical permissions granted"
        } else {
            "⚠ $missingCritical critical permission(s) missing — tap to fix"
        }

        val inflater = LayoutInflater.from(this)
        for (status in statuses) {
            val row = ItemPermissionBinding.inflate(inflater, binding.container, false)
            row.tvName.text = PermissionManager.label(status.key)
            row.tvDescription.text = PermissionManager.description(status.key)
            row.tvStatus.text = if (status.granted) "GRANTED" else "MISSING"
            row.tvStatus.setTextColor(
                if (status.granted) 0xFF43A047.toInt() else 0xFFE53935.toInt()
            )
            row.btnGrant.text = if (status.granted) "Re-check" else "Grant"
            row.btnGrant.setOnClickListener { launchIntentFor(status.key) }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * resources.displayMetrics.density).toInt() }
            binding.container.addView(row.root, lp)
        }
    }

    private fun launchIntentFor(key: PermissionKey) {
        val intent = PermissionManager.intentFor(this, key) ?: return
        runCatching { systemSettings.launch(intent) }
            .onFailure {
                // Fallback to plain settings if a vendor doesn't handle the action.
                runCatching {
                    systemSettings.launch(Intent(android.provider.Settings.ACTION_SETTINGS))
                }
            }
    }
}
