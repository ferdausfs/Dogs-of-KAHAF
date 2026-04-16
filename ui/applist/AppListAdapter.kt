package com.kahaf.guardian.ui.applist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kahaf.guardian.databinding.ItemAppBinding
import com.kahaf.guardian.domain.model.AppInfo
import com.kahaf.guardian.util.PackageUtils

class AppListAdapter(
    private val onBlockToggle: (AppInfo) -> Unit,
    private val onWhitelistToggle: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(
        private val binding: ItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            binding.tvAppName.text = app.appName
            binding.tvPackageName.text = app.packageName

            // Load icon
            val icon = PackageUtils.getAppIcon(binding.root.context, app.packageName)
            binding.ivAppIcon.setImageDrawable(icon)

            // Set chip states without triggering listeners
            binding.chipBlock.setOnCheckedChangeListener(null)
            binding.chipWhitelist.setOnCheckedChangeListener(null)

            binding.chipBlock.isChecked = app.isBlocked
            binding.chipWhitelist.isChecked = app.isWhitelisted

            binding.chipBlock.setOnClickListener {
                onBlockToggle(app)
            }

            binding.chipWhitelist.setOnClickListener {
                onWhitelistToggle(app)
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }
}