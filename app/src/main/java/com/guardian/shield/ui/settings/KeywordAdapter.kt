package com.guardian.shield.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardian.shield.databinding.ItemKeywordBinding
import com.guardian.shield.domain.model.KeywordRule

class KeywordAdapter(val onDelete: (KeywordRule) -> Unit) :
    ListAdapter<KeywordRule, KeywordAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<KeywordRule>() {
            override fun areItemsTheSame(o: KeywordRule, n: KeywordRule) = o.id == n.id
            override fun areContentsTheSame(o: KeywordRule, n: KeywordRule) = o == n
        }
    }

    inner class VH(val b: ItemKeywordBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemKeywordBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val k = getItem(pos)
        h.b.tvKeyword.text = k.keyword + (if (k.isRegex) " [regex]" else "")
        h.b.btnDelete.setOnClickListener { onDelete(k) }
    }
}
