package com.guardian.shield.ui.settings

import android.content.pm.PackageManager
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardian.shield.R
import com.guardian.shield.databinding.ItemAppRuleBinding
import com.guardian.shield.domain.model.AppRule

class AppListAdapter(
    private val pm: PackageManager,
    private val isLocked: Boolean = false,
    private val onBlockChanged: (String, Boolean) -> Unit,
    private val onWhitelistChanged: (String, Boolean) -> Unit
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
            val ctx = b.root.context
            b.txtAppName.text = rule.appName.ifBlank { rule.packageName }
            b.txtPackage.text = rule.packageName
            try {
                val icon = pm.getApplicationIcon(rule.packageName)
                b.imgIcon.setImageDrawable(icon)
            } catch (_: Throwable) {
                b.imgIcon.setImageDrawable(null)
            }

            // Clear listeners before setting checked state to avoid spurious callbacks
            b.switchBlock.setOnCheckedChangeListener(null)
            b.switchWhitelist.setOnCheckedChangeListener(null)
            b.switchBlock.isChecked = rule.isBlocked
            b.switchWhitelist.isChecked = rule.isWhitelisted
            b.switchBlock.isEnabled = !isLocked
            b.switchWhitelist.isEnabled = !isLocked

            // ===== TASK 1: One-Way Block Rule =====
            // If app is blocked, hide the whitelist switch row entirely.
            // Whitelist -> block is allowed, but block -> whitelist is forbidden.
            if (rule.isBlocked) {
                b.rowWhitelist.visibility = View.GONE
                b.switchWhitelist.visibility = View.GONE
                b.txtWhitelistLabel.visibility = View.GONE
            } else {
                b.rowWhitelist.visibility = View.VISIBLE
                b.switchWhitelist.visibility = View.VISIBLE
                b.txtWhitelistLabel.visibility = View.VISIBLE
            }

            // ===== TASK 5: UI Improvements — left accent + status badge =====
            when {
                rule.isBlocked -> {
                    b.leftAccent.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.block_indicator)
                    )
                    b.badgeStatus.visibility = View.VISIBLE
                    b.badgeStatus.text = "BLOCKED"
                    b.badgeStatus.setBackgroundResource(R.drawable.bg_status_badge)
                    b.badgeStatus.setTextColor(Color.WHITE)
                }
                rule.isWhitelisted -> {
                    b.leftAccent.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.allow_indicator)
                    )
                    b.badgeStatus.visibility = View.VISIBLE
                    b.badgeStatus.text = "ALLOWED"
                    b.badgeStatus.setBackgroundResource(R.drawable.bg_status_badge_allow)
                    b.badgeStatus.setTextColor(Color.BLACK)
                }
                else -> {
                    b.leftAccent.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.on_surface_dim)
                    )
                    b.badgeStatus.visibility = View.GONE
                }
            }

            b.switchBlock.setOnCheckedChangeListener { _, v ->
                onBlockChanged(rule.packageName, v)
            }
            b.switchWhitelist.setOnCheckedChangeListener { _, v ->
                // Extra safety guard at UI layer — should never fire while blocked
                // because the row is hidden, but keep this for defensive correctness.
                if (rule.isBlocked) return@setOnCheckedChangeListener
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
