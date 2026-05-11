package com.guardian.shield.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardian.shield.R
import com.guardian.shield.databinding.ItemBlockEventBinding
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockEventAdapter(
    private val onDelete: (BlockEvent) -> Unit
) : ListAdapter<BlockEvent, BlockEventAdapter.VH>(DIFF) {

    fun submit(list: List<BlockEvent>) { submitList(list) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemBlockEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemBlockEventBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: BlockEvent) {
            b.txtPackage.text = e.packageName
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val time = fmt.format(Date(e.timestamp))
            val reasonText = when (e.reason) {
                BlockReason.AI_DETECTION -> b.root.context.getString(R.string.reason_ai)
                BlockReason.KEYWORD_MATCH -> b.root.context.getString(R.string.reason_kw)
                BlockReason.APP_BLOCKED -> b.root.context.getString(R.string.reason_app)
                BlockReason.SCHEDULE_BLOCKED -> b.root.context.getString(R.string.reason_sched)
                BlockReason.MANUAL -> b.root.context.getString(R.string.reason_manual)
            }
            b.txtReason.text = "$reasonText • $time"
            val color = when (e.reason) {
                BlockReason.AI_DETECTION -> R.color.primary
                BlockReason.KEYWORD_MATCH -> R.color.secondary
                BlockReason.APP_BLOCKED -> R.color.error
                BlockReason.SCHEDULE_BLOCKED -> R.color.purple
                BlockReason.MANUAL -> R.color.on_surface_dim
            }
            b.badge.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(b.root.context.getColor(color))
            )
            b.root.setOnLongClickListener { onDelete(e); true }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<BlockEvent>() {
            override fun areItemsTheSame(o: BlockEvent, n: BlockEvent) = o.id == n.id
            override fun areContentsTheSame(o: BlockEvent, n: BlockEvent) = o == n
        }
    }
}
