package com.guardianshield.app.ui.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardianshield.app.data.model.ActivityLog
import com.guardianshield.app.databinding.ItemActivityLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityLogAdapter : ListAdapter<ActivityLog, ActivityLogAdapter.VH>(DIFF) {

    private val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    inner class VH(val b: ItemActivityLogBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemActivityLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val log = getItem(position)
        h.b.txtTime.text = fmt.format(Date(log.timestamp))
        h.b.txtIcon.text = when (log.eventType) {
            "BLOCK" -> "🚫"
            "AI_WARN" -> "⚠️"
            "AI_BLOCK_24H" -> "🚫"
            "SCROLL_REMINDER" -> "📖"
            "KEYWORD" -> "🔤"
            "SCHEDULE" -> "⏰"
            "TAMPER" -> "🛡️"
            else -> "•"
        }
        h.b.txtTitle.text = log.appLabel.ifEmpty { log.packageName }
        h.b.txtDetails.text = when (log.eventType) {
            "AI_WARN" -> "AI Warning — Strike ${log.strikeCount}/3"
            "AI_BLOCK_24H" -> "🚫 24-hour block (Strike 3/3)"
            "SCROLL_REMINDER" -> "Scroll addiction — Quran reminder shown"
            else -> log.details.ifEmpty { log.packageName }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ActivityLog>() {
            override fun areItemsTheSame(a: ActivityLog, b: ActivityLog) = a.id == b.id
            override fun areContentsTheSame(a: ActivityLog, b: ActivityLog) = a == b
        }
    }
}
