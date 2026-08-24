package com.guardian.shield.ui.settings

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guardian.shield.databinding.ItemAppPickBinding

/** One installed, launchable app as shown in the schedule editor picker. */
data class AppPick(
    val packageName: String,
    val label: String,
    val selected: Boolean = false
)

/**
 * R7.3 — searchable premium app picker for the schedule editor. The adapter
 * owns the FULL installed-app list and re-filters on every keystroke;
 * selection state mirrors the editor text field (field text == source of
 * truth, so Save stays a plain text read — zero editor-logic changes).
 */
class AppPickAdapter(
    private val pm: PackageManager,
    private val onPick: (AppPick) -> Unit
) : ListAdapter<AppPick, AppPickAdapter.VH>(DIFF) {

    private var full: List<AppPick> = emptyList()
    private var loaded = false

    fun hasData(): Boolean = loaded

    fun setData(apps: List<AppPick>, selectedPkg: String) {
        full = apps
        loaded = true
        submitFiltered("", selectedPkg)
    }

    /** Re-filter by [query]; the row equal to [selectedPkg] gets the check. */
    fun submitFiltered(query: String, selectedPkg: String): Int {
        val q = query.trim().lowercase()
        val base = if (q.isBlank()) full else full.filter {
            it.label.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q)
        }
        val shown = base.map { it.copy(selected = it.packageName == selectedPkg) }
        submitList(shown)
        return shown.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemAppPickBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemAppPickBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: AppPick) {
            b.txtPickName.text = item.label
            b.txtPickPkg.text = item.packageName
            runCatching {
                b.imgPickIcon.setImageDrawable(pm.getApplicationIcon(item.packageName))
            }.onFailure { b.imgPickIcon.setImageDrawable(null) }
            b.imgPicked.visibility = if (item.selected) View.VISIBLE else View.GONE
            b.root.setOnClickListener { onPick(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppPick>() {
            override fun areItemsTheSame(o: AppPick, n: AppPick) = o.packageName == n.packageName
            override fun areContentsTheSame(o: AppPick, n: AppPick) = o == n
        }
    }
}
