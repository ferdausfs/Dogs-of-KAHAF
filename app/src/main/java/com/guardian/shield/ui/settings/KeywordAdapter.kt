package com.guardian.shield.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardian.shield.databinding.ItemKeywordBinding
import com.guardian.shield.domain.model.KeywordRule

class KeywordAdapter(
    private val onDelete: (KeywordRule) -> Unit
) : ListAdapter<KeywordRule, KeywordAdapter.VH>(DIFF) {

    fun submit(list: List<KeywordRule>) { submitList(list) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemKeywordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemKeywordBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(rule: KeywordRule) {
            b.txtKeyword.text = rule.keyword
            b.badge.text = if (rule.isRegex) "REGEX" else "PLAIN"
            b.btnDelete.setOnClickListener { onDelete(rule) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<KeywordRule>() {
            override fun areItemsTheSame(o: KeywordRule, n: KeywordRule) = o.id == n.id
            override fun areContentsTheSame(o: KeywordRule, n: KeywordRule) = o == n
        }
    }
}
