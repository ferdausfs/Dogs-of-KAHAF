package com.guardian.shield.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardian.shield.databinding.ItemBlockEventBinding
import com.guardian.shield.domain.model.BlockEvent
import java.text.SimpleDateFormat
import java.util.*

class BlockEventAdapter : ListAdapter<BlockEvent, BlockEventAdapter.VH>(DIFF) {

    companion object {
        private val FMT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        private val DIFF = object : DiffUtil.ItemCallback<BlockEvent>() {
            override fun areItemsTheSame(o: BlockEvent, n: BlockEvent) = o.id == n.id
            override fun areContentsTheSame(o: BlockEvent, n: BlockEvent) = o == n
        }
    }

    fun submit(items: List<BlockEvent>) = submitList(items)

    inner class VH(val b: ItemBlockEventBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemBlockEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.b.tvPackage.text = item.packageName
        holder.b.tvReason.text = "${item.reason.name}${item.matchedTerm?.let { " · $it" } ?: ""}"
        holder.b.tvTime.text = FMT.format(Date(item.timestamp))
    }
}
