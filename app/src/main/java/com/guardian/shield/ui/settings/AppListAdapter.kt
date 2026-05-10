package com.guardian.shield.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
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

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
        ItemAppBinding.inflate(LayoutInflater.from(p.context), p, false)
    )

    override fun onBindViewHolder(h: VH, pos: Int) {
        val app = getItem(pos)
        h.b.tvName.text = app.name
        h.b.tvPackage.text = app.pkg
        h.b.cbBlock.setOnCheckedChangeListener(null)
        h.b.cbWhitelist.setOnCheckedChangeListener(null)
        h.b.cbBlock.isChecked = app.rule?.isBlocked == true
        h.b.cbWhitelist.isChecked = app.rule?.isWhitelisted == true
        h.b.cbBlock.setOnCheckedChangeListener { _, _ -> onBlockToggle(app) }
        h.b.cbWhitelist.setOnCheckedChangeListener { _, _ -> onWhitelistToggle(app) }
    }
}
