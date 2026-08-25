package com.guardian.shield.ui.settings

import android.content.pm.PackageManager
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardian.shield.databinding.ItemAppRuleBinding
import com.guardian.shield.domain.model.AppRule

class AppListAdapter(
    private val pm: PackageManager,
    private val isLocked: Boolean = false,
    private val onBlockChanged: (String, Boolean) -> Unit,
    private val onWhitelistChanged: (String, Boolean) -> Unit,
    // R12 (v3.8.2) — undo an accidental block within the 3-minute grace
    // window. Works (deliberately) even while a Time-Lock is active.
    private val onUndoBlocked: (String) -> Unit = {}
) : ListAdapter<AppRule, AppListAdapter.VH>(DIFF) {

    fun submit(list: List<AppRule>) { submitList(list) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemAppRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemAppRuleBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(rule: AppRule) {
            b.txtAppName.text = rule.appName.ifBlank { rule.packageName }
            b.txtPackage.text = rule.packageName
            try {
                val icon = pm.getApplicationIcon(rule.packageName)
                b.imgIcon.setImageDrawable(icon)
            } catch (_: Throwable) {
                b.imgIcon.setImageDrawable(null)
            }

            // Detach listeners before mutating UI to avoid recursive callbacks
            b.switchBlock.setOnCheckedChangeListener(null)
            b.switchWhitelist.setOnCheckedChangeListener(null)

            b.switchBlock.isChecked = rule.isBlocked
            b.switchWhitelist.isChecked = rule.isWhitelisted
            b.switchBlock.isEnabled = !isLocked
            b.switchWhitelist.isEnabled = !isLocked

            // TASK 1: One-way block rule — if blocked, hide whitelist switch entirely.
            // An app moved to blocklist cannot be moved back to allowlist.
            if (rule.isBlocked) {
                b.switchWhitelist.visibility = View.GONE
            } else {
                b.switchWhitelist.visibility = View.VISIBLE
            }

            // Premium badges — show BLOCKED / ALLOWED chip with design system colors
            try {
                if (rule.isBlocked) {
                    b.txtStatusBadge.visibility = View.VISIBLE
                    b.txtStatusBadge.text = "BLOCKED"
                    b.txtStatusBadge.setTextColor(b.root.context.getColor(com.guardian.shield.R.color.error))
                    b.imgLockIcon.visibility = View.VISIBLE
                } else if (rule.isWhitelisted) {
                    b.txtStatusBadge.visibility = View.VISIBLE
                    b.txtStatusBadge.text = "ALLOWED"
                    b.txtStatusBadge.setTextColor(b.root.context.getColor(com.guardian.shield.R.color.success))
                    b.imgLockIcon.visibility = View.GONE
                } else {
                    // R13 (v3.8.2) — an app with NO rule was indistinguishable
                    // from "unset" (badge hidden); users could not tell it is
                    // allowed BY DEFAULT. Say it explicitly in a muted chip.
                    b.txtStatusBadge.visibility = View.VISIBLE
                    b.txtStatusBadge.text = b.root.context.getString(
                        com.guardian.shield.R.string.app_badge_default
                    )
                    b.txtStatusBadge.setTextColor(
                        b.root.context.getColor(com.guardian.shield.R.color.on_surface_variant)
                    )
                    b.imgLockIcon.visibility = View.GONE
                }
            } catch (_: Throwable) {}

            // R12 — undo chip: only while the 3-minute grace window is open.
            try {
                val remaining = AppRule.graceRemainingMs(rule.blockedAtMs)
                if (rule.isBlocked && AppRule.isWithinGrace(rule.blockedAtMs) && remaining > 0L) {
                    val mins = ((remaining + 59_999L) / 60_000L).coerceIn(1L, 3L)
                    b.btnUndoBlock.visibility = View.VISIBLE
                    b.btnUndoBlock.text = b.root.context.getString(
                        com.guardian.shield.R.string.undo_block_chip, mins
                    )
                    b.btnUndoBlock.setOnClickListener { onUndoBlocked(rule.packageName) }
                } else {
                    b.btnUndoBlock.visibility = View.GONE
                    b.btnUndoBlock.setOnClickListener(null)
                }
            } catch (_: Throwable) {}

            // Optional category badge — heuristic from package name
            try {
                val pkg = rule.packageName.lowercase()
                val cat = when {
                    pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("browser") || pkg.contains("opera") || pkg.contains("brave") -> "Browser"
                    pkg.contains("facebook") || pkg.contains("instagram") || pkg.contains("tiktok") || pkg.contains("twitter") || pkg.contains("reddit") -> "Social"
                    pkg.contains("youtube") || pkg.contains("video") || pkg.contains("tiktok") -> "Video"
                    pkg.contains("messenger") || pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("imo") -> "Messaging"
                    else -> ""
                }
                if (cat.isNotEmpty()) {
                    b.txtCategory.visibility = View.VISIBLE
                    b.txtCategory.text = cat
                } else {
                    b.txtCategory.visibility = View.GONE
                }
            } catch (_: Throwable) {
                try { b.txtCategory.visibility = View.GONE } catch (_: Throwable) {}
            }

            // Left side indicator strip — premium colors
            try {
                when {
                    rule.isBlocked -> b.viewLeftIndicator.setBackgroundColor(b.root.context.getColor(com.guardian.shield.R.color.error))
                    rule.isWhitelisted -> b.viewLeftIndicator.setBackgroundColor(b.root.context.getColor(com.guardian.shield.R.color.success))
                    else -> b.viewLeftIndicator.setBackgroundColor(Color.TRANSPARENT)
                }
            } catch (_: Throwable) {}

            b.switchBlock.setOnCheckedChangeListener { _, v ->
                onBlockChanged(rule.packageName, v)
            }
            b.switchWhitelist.setOnCheckedChangeListener { _, v ->
                // Extra defensive guard: never allow whitelist toggle while blocked
                if (rule.isBlocked) {
                    b.switchWhitelist.isChecked = false
                    return@setOnCheckedChangeListener
                }
                onWhitelistChanged(rule.packageName, v)
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppRule>() {
            override fun areItemsTheSame(o: AppRule, n: AppRule) = o.packageName == n.packageName
            override fun areContentsTheSame(o: AppRule, n: AppRule) = o == n
        }
    }
}
