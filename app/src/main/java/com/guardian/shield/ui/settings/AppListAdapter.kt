package com.guardian.shield.ui.settings

import android.content.pm.PackageManager
import android.view.LayoutInflater
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
            b.switchBlock.setOnCheckedChangeListener(null)
            b.switchWhitelist.setOnCheckedChangeListener(null)
            b.switchBlock.isChecked = rule.isBlocked
            b.switchWhitelist.isChecked = rule.isWhitelisted
            b.switchBlock.isEnabled = !isLocked
            b.switchWhitelist.isEnabled = !isLocked
            b.switchBlock.setOnCheckedChangeListener { _, v ->
                onBlockChanged(rule.packageName, v)
            }
            b.switchWhitelist.setOnCheckedChangeListener { _, v ->
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
