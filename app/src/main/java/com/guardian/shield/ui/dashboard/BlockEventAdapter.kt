package com.guardian.shield.ui.dashboard

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
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
    private val pm: PackageManager,
    private val onDelete: (BlockEvent) -> Unit
) : ListAdapter<BlockEvent, BlockEventAdapter.VH>(DIFF) {

    private var lastAnimatedPosition = -1

    fun submit(list: List<BlockEvent>) {
        submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemBlockEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
        if (position > lastAnimatedPosition) {
            val anim = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.item_slide_in)
            anim.startOffset = (position * 40L).coerceAtMost(200L)
            holder.itemView.startAnimation(anim)
            lastAnimatedPosition = position
        }
    }

    inner class VH(private val binding: ItemBlockEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: BlockEvent) {
            val ctx = binding.root.context
            binding.txtPackage.text = event.packageName.substringAfterLast('.')
                .replaceFirstChar { it.uppercase() }
                .ifBlank { event.packageName }

            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.txtTime.text = fmt.format(Date(event.timestamp))

            val (reasonText, colorRes, emoji) = when (event.reason) {
                BlockReason.AI_DETECTION -> Triple(ctx.getString(R.string.reason_ai), R.color.primary, "🤖")
                BlockReason.KEYWORD_MATCH -> Triple(ctx.getString(R.string.reason_kw), R.color.secondary, "🔑")
                BlockReason.APP_BLOCKED -> Triple(ctx.getString(R.string.reason_app), R.color.error, "🚫")
                BlockReason.SCHEDULE_BLOCKED -> Triple(ctx.getString(R.string.reason_sched), R.color.purple, "🕐")
                BlockReason.MANUAL -> Triple(ctx.getString(R.string.reason_manual), R.color.on_surface_dim, "✋")
            }

            binding.txtReason.text = "$emoji $reasonText"
            binding.txtDetails.text = event.matchedTerm?.takeIf { it.isNotBlank() }
                ?.let { ctx.getString(R.string.block_detail_term_fmt, it) }
                ?: ctx.getString(R.string.block_detail_package_fmt, event.packageName)

            val color = ctx.getColor(colorRes)
            binding.badge.backgroundTintList = android.content.res.ColorStateList.valueOf(color)

            try {
                binding.imgAppIcon.setImageDrawable(pm.getApplicationIcon(event.packageName))
            } catch (_: Throwable) {
                binding.imgAppIcon.setImageResource(R.drawable.ic_app_placeholder)
            }

            binding.root.setOnLongClickListener {
                binding.root.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(200)
                    .withEndAction { onDelete(event) }
                    .start()
                true
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<BlockEvent>() {
            override fun areItemsTheSame(oldItem: BlockEvent, newItem: BlockEvent) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: BlockEvent, newItem: BlockEvent) = oldItem == newItem
        }
    }
}
