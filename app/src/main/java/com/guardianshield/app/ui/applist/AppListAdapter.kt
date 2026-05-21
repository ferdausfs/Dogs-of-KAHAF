package com.guardianshield.app.ui.applist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardianshield.app.databinding.ItemAppRuleBinding

class AppListAdapter(
    private val onBlockToggle: (AppListItem, Boolean) -> Unit,
    private val onAllowToggle: (AppListItem, Boolean) -> Unit,
    private val onBlockedWhileWhitelisted: () -> Unit
) : ListAdapter<AppListItem, AppListAdapter.VH>(DIFF) {

    inner class VH(val b: ItemAppRuleBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemAppRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val rule = getItem(position)
        val b = h.b

        b.imgIcon.setImageDrawable(rule.icon)
        val suffix = if (rule.isWhitelisted) " [✅ Allowed]" else ""
        b.txtLabel.text = rule.label
        b.txtPackage.text = rule.packageName + suffix

        // ---- v2 One-way rule: disable Block switch when whitelisted ----
        val canBlock = !rule.isLocked && !rule.isWhitelisted

        // Detach listeners before mutating checked state.
        b.switchBlock.setOnCheckedChangeListener(null)
        b.switchAllow.setOnCheckedChangeListener(null)

        b.switchBlock.isEnabled = canBlock
        b.switchBlock.alpha = if (canBlock) 1.0f else 0.38f
        b.switchBlock.isChecked = rule.isBlocked
        b.switchAllow.isEnabled = !rule.isLocked
        b.switchAllow.isChecked = rule.isWhitelisted

        // Intercept clicks on disabled switch to inform the user.
        b.switchBlock.setOnClickListener {
            if (rule.isWhitelisted) {
                b.switchBlock.isChecked = false
                onBlockedWhileWhitelisted()
            }
        }

        b.switchBlock.setOnCheckedChangeListener { _, isChecked ->
            if (rule.isWhitelisted && isChecked) {
                // Should not happen (disabled), but defensive.
                b.switchBlock.isChecked = false
                onBlockedWhileWhitelisted()
                return@setOnCheckedChangeListener
            }
            onBlockToggle(rule, isChecked)
        }
        b.switchAllow.setOnCheckedChangeListener { _, isChecked ->
            onAllowToggle(rule, isChecked)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppListItem>() {
            override fun areItemsTheSame(a: AppListItem, b: AppListItem) = a.packageName == b.packageName
            override fun areContentsTheSame(a: AppListItem, b: AppListItem) = a == b
        }
    }
}
