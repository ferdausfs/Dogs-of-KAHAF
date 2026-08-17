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

    fun submit(list: List<BlockEvent>) { submitList(list) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemBlockEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
        // Slide-in animation for new items
        if (position > lastAnimatedPosition) {
            val anim = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.item_slide_in)
            anim.startOffset = (position * 40L).coerceAtMost(200L)
            holder.itemView.startAnimation(anim)
            lastAnimatedPosition = position
        }
    }

    inner class VH(private val b: ItemBlockEventBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: BlockEvent) {
            b.txtPackage.text = e.packageName.substringAfterLast('.')
                .replaceFirstChar { it.uppercase() }
                .ifBlank { e.packageName }

            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val time = fmt.format(Date(e.timestamp))
            val ctx = b.root.context

            val (reasonText, colorRes, emoji) = when (e.reason) {
                BlockReason.AI_DETECTION    -> Triple(ctx.getString(R.string.reason_ai),    R.color.primary,       "🤖")
                BlockReason.KEYWORD_MATCH   -> Triple(ctx.getString(R.string.reason_kw),    R.color.secondary,     "🔑")
                BlockReason.APP_BLOCKED     -> Triple(ctx.getString(R.string.reason_app),   R.color.error,         "🚫")
                BlockReason.SCHEDULE_BLOCKED-> Triple(ctx.getString(R.string.reason_sched), R.color.purple,        "🕐")
                BlockReason.MANUAL          -> Triple(ctx.getString(R.string.reason_manual),R.color.on_surface_dim,"✋")
                BlockReason.TAMPER_ATTEMPT   -> Triple(ctx.getString(R.string.reason_tamper), R.color.error,         "⚠️")
            }

            b.txtReason.text = "$emoji $reasonText"
            b.txtTime.text   = time
            try { b.txtTimeInline.text = time } catch (_: Throwable) {}
            try {
                b.txtBadgeBlocked.text = when (e.reason) {
                    BlockReason.APP_BLOCKED -> "Blocked"
                    BlockReason.SCHEDULE_BLOCKED -> "Schedule"
                    BlockReason.AI_DETECTION -> "AI Blocked"
                    BlockReason.KEYWORD_MATCH -> "Keyword"
                    else -> "Blocked"
                }
            } catch (_: Throwable) {}

            val color = ctx.getColor(colorRes)
            b.badge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color))

            // App icon
            try {
                val icon = pm.getApplicationIcon(e.packageName)
                b.imgAppIcon.setImageDrawable(icon)
            } catch (_: Throwable) {
                b.imgAppIcon.setImageResource(R.drawable.ic_app_placeholder)
            }

            b.root.setOnClickListener {
                // Optional: open Blocked Content details (flagged for approval, approved in v2 mockup)
                runCatching {
                    val ctx = b.root.context
                    val intent = android.content.Intent(ctx, com.guardian.shield.ui.overlay.BlockedDetailActivity::class.java).apply {
                        putExtra(com.guardian.shield.ui.overlay.BlockedDetailActivity.EXTRA_PACKAGE, e.packageName)
                        putExtra(com.guardian.shield.ui.overlay.BlockedDetailActivity.EXTRA_CATEGORY, e.reason.name)
                        putExtra(com.guardian.shield.ui.overlay.BlockedDetailActivity.EXTRA_SOURCE, e.packageName)
                        putExtra(com.guardian.shield.ui.overlay.BlockedDetailActivity.EXTRA_TIME, b.txtTime.text.toString())
                    }
                    ctx.startActivity(intent)
                }
            }

            b.root.setOnLongClickListener {
                b.root.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f)
                    .setDuration(200).withEndAction { onDelete(e) }.start()
                true
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<BlockEvent>() {
            override fun areItemsTheSame(o: BlockEvent, n: BlockEvent) = o.id == n.id
            override fun areContentsTheSame(o: BlockEvent, n: BlockEvent) = o == n
        }
    }
}
