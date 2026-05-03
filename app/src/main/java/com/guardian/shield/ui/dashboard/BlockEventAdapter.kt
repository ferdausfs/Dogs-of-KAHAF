package com.guardian.shield.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardian.shield.databinding.ItemBlockEventBinding
import com.guardian.shield.domain.model.BlockEvent
import com.guardian.shield.domain.model.BlockReason
import java.text.SimpleDateFormat
import java.util.*

class BlockEventAdapter :
    ListAdapter<BlockEvent, BlockEventAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BlockEvent>() {
            override fun areItemsTheSame(a: BlockEvent, b: BlockEvent) = a.id == b.id
            override fun areContentsTheSame(a: BlockEvent, b: BlockEvent) = a == b
        }
    }

    // FIX: ThreadLocal<SimpleDateFormat> — SimpleDateFormat is NOT thread-safe
    // RecyclerView can bind items from multiple threads
    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm, MMM dd", Locale.getDefault())
    }

    // FIX: EventDisplay data class — eliminates duplicate when{} blocks
    private data class EventDisplay(val icon: String, val reason: String)

    private fun BlockEvent.toDisplay(): EventDisplay = when (reason) {
        BlockReason.APP_BLOCKED      -> EventDisplay("📵", "App blocked")
        BlockReason.KEYWORD_DETECTED -> EventDisplay("🔤", "Keyword: $detail")
        BlockReason.AI_DETECTED      -> EventDisplay("🤖", "AI: $detail")
    }

    inner class ViewHolder(private val binding: ItemBlockEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(event: BlockEvent) {
            val display = event.toDisplay()
            binding.tvAppName.text = event.appName
            binding.tvTime.text    = timeFormat.get()
                ?.format(Date(event.timestamp)) ?: ""
            binding.tvReason.text  = display.reason
            binding.tvIcon.text    = display.icon
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBlockEventBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}