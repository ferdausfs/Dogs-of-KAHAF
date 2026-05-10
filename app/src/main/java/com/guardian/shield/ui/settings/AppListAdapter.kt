package com.guardian.shield.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardian.shield.databinding.ItemAppBinding
import com.guardian.shield.viewmodel.InstalledApp

class AppListAdapter(
    val onBlockToggle: (InstalledApp) -> Unit,
    val onWhitelistToggle: (InstalledApp) -> Unit
) : ListAdapter<InstalledApp, AppListAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<InstalledApp>() {
            override fun areItemsTheSame(o: InstalledApp, n: InstalledApp) = o.pkg == n.pkg
            override fun areContentsTheSame(o: InstalledApp, n: InstalledApp) = o == n
        }
    }

    inner class VH(val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = getItem(position)
        holder.b.tvName.text = app.name
        holder.b.tvPackage.text = app.pkg

        val tags = buildList {
            if (app.isSystemApp) add("System app")
            if (app.isAlwaysAllowed) add("Always allowed")
        }
        holder.b.tvMeta.isVisible = tags.isNotEmpty()
        holder.b.tvMeta.text = tags.joinToString(" • ")

        holder.b.cbBlock.setOnCheckedChangeListener(null)
        holder.b.cbWhitelist.setOnCheckedChangeListener(null)

        holder.b.cbBlock.isEnabled = !app.isAlwaysAllowed
        holder.b.cbWhitelist.isEnabled = !app.isAlwaysAllowed
        holder.b.cbBlock.alpha = if (app.isAlwaysAllowed) 0.45f else 1f
        holder.b.cbWhitelist.alpha = if (app.isAlwaysAllowed) 0.75f else 1f

        holder.b.cbBlock.isChecked = !app.isAlwaysAllowed && app.rule?.isBlocked == true
        holder.b.cbWhitelist.isChecked = app.isAlwaysAllowed || app.rule?.isWhitelisted == true

        holder.b.cbBlock.setOnCheckedChangeListener { _, _ -> onBlockToggle(app) }
        holder.b.cbWhitelist.setOnCheckedChangeListener { _, _ -> onWhitelistToggle(app) }
    }
}
