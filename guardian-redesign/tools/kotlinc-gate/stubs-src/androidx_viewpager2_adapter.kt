// GATE STUB — FragmentStateAdapter.
package androidx.viewpager2.adapter

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView

abstract class FragmentStateAdapter : RecyclerView.Adapter<FragmentStateAdapter.FragmentViewHolder> {
    constructor(fragmentActivity: FragmentActivity) : super()
    constructor(fragment: Fragment) : super()

    abstract fun createFragment(position: Int): Fragment

    final override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): FragmentViewHolder =
        throw RuntimeException("stub")

    final override fun onBindViewHolder(holder: FragmentViewHolder, position: Int) {}

    class FragmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
