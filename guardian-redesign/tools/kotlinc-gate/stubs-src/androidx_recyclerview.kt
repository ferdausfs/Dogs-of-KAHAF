// GATE STUB — androidx.recyclerview.
package androidx.recyclerview.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup

@Suppress("DEPRECATION")
open class RecyclerView(context: Context) : ViewGroup(context) {
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
    var layoutManager: LayoutManager?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") v) {}
    var adapter: Adapter<*>?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") v) {}
    fun setHasFixedSize(fixed: Boolean) {}
    fun addItemDecoration(decor: ItemDecoration) {}
    fun smoothScrollToPosition(position: Int) {}

    abstract class ViewHolder(val itemView: View) {
        val bindingAdapterPosition: Int get() = 0
        val adapterPosition: Int get() = 0
        val absoluteAdapterPosition: Int get() = 0
    }

    abstract class Adapter<VH : ViewHolder> {
        abstract fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH
        abstract fun onBindViewHolder(holder: VH, position: Int)
        abstract fun getItemCount(): Int
        open fun getItemId(position: Int): Long = -1L
        open fun getItemViewType(position: Int): Int = 0
        fun notifyDataSetChanged() {}
        fun notifyItemInserted(position: Int) {}
        fun notifyItemRemoved(position: Int) {}
        fun notifyItemChanged(position: Int) {}
        fun notifyItemRangeInserted(start: Int, count: Int) {}
    }

    abstract class LayoutManager

    open class ItemDecoration
}

open class LinearLayoutManager : RecyclerView.LayoutManager {
    constructor(context: Context) : super()
    constructor(context: Context, orientation: Int, reverseLayout: Boolean) : super()

    companion object {
        const val HORIZONTAL: Int = 0
        const val VERTICAL: Int = 1
    }
}

object DiffUtil {
    abstract class ItemCallback<T> {
        abstract fun areItemsTheSame(oldItem: T, newItem: T): Boolean
        abstract fun areContentsTheSame(oldItem: T, newItem: T): Boolean
        open fun getChangePayload(oldItem: T, newItem: T): Any? = null
    }
}

@Suppress("DEPRECATION")
abstract class ListAdapter<T, VH : RecyclerView.ViewHolder>(
    protected val diffCallback: DiffUtil.ItemCallback<T>
) : RecyclerView.Adapter<VH>() {
    protected var items: List<T> = emptyList()
    val currentList: List<T> get() = items
    protected fun getItem(position: Int): T = items[position]
    open fun submitList(list: List<T>?) { items = list ?: emptyList() }
    override fun getItemCount(): Int = items.size
}
