package com.kahaf.guardian.ui.applist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kahaf.guardian.databinding.ItemAppBinding
import com.kahaf.guardian.domain.model.AppInfo
import com.kahaf.guardian.util.PackageUtils

class AppListAdapter(private val onBlock: (AppInfo) -> Unit, private val onWhitelist: (AppInfo) -> Unit) :
    ListAdapter<AppInfo, AppListAdapter.VH>(object : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(a: AppInfo, b: AppInfo) = a.packageName == b.packageName
        override fun areContentsTheSame(a: AppInfo, b: AppInfo) = a == b
    }) {
    inner class VH(private val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(app: AppInfo) {
            b.tvAppName.text = app.appName; b.tvPackageName.text = app.packageName
            b.ivAppIcon.setImageDrawable(PackageUtils.getAppIcon(b.root.context, app.packageName))
            b.chipBlock.setOnCheckedChangeListener(null); b.chipWhitelist.setOnCheckedChangeListener(null)
            b.chipBlock.isChecked = app.isBlocked; b.chipWhitelist.isChecked = app.isWhitelisted
            b.chipBlock.setOnClickListener { onBlock(app) }; b.chipWhitelist.setOnClickListener { onWhitelist(app) }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
