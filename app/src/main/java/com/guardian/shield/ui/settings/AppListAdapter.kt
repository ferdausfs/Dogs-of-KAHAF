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

            // TASK 5: Subtle status badge — show ALLOWED / BLOCKED chip if available
            try {
                if (rule.isBlocked) {
                    b.txtStatusBadge.visibility = View.VISIBLE
                    b.txtStatusBadge.text = "BLOCKED"
                    b.txtStatusBadge.setTextColor(Color.parseColor("#FF4444"))
                } else if (rule.isWhitelisted) {
                    b.txtStatusBadge.visibility = View.VISIBLE
                    b.txtStatusBadge.text = "ALLOWED"
                    b.txtStatusBadge.setTextColor(Color.parseColor("#00E5CC"))
                } else {
                    b.txtStatusBadge.visibility = View.GONE
                }
            } catch (_: Throwable) {
                // If layout doesn't expose this view (older build), silently ignore
            }

            // Left side indicator strip
            try {
                when {
                    rule.isBlocked -> b.viewLeftIndicator.setBackgroundColor(Color.parseColor("#FF4444"))
                    rule.isWhitelisted -> b.viewLeftIndicator.setBackgroundColor(Color.parseColor("#00E5CC"))
                    else -> b.viewLeftIndicator.setBackgroundColor(Color.TRANSPARENT)
                }
            } catch (_: Throwable) { /* optional */ }

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
