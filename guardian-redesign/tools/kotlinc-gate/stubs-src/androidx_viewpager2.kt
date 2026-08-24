// GATE STUB — androidx.viewpager2.
package androidx.viewpager2.widget

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

open class ViewPager2(context: Context) : ViewGroup(context) {
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
    var adapter: RecyclerView.Adapter<*>?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") v) {}
    var orientation: Int = 0
    var currentItem: Int = 0
    var offscreenPageLimit: Int = 0
    var isUserInputEnabled: Boolean = true
    fun registerOnPageChangeCallback(callback: OnPageChangeCallback) {}
    fun setCurrentItem(item: Int, smoothScroll: Boolean) {}

    open class OnPageChangeCallback {
        open fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
        open fun onPageSelected(position: Int) {}
        open fun onPageScrollStateChanged(state: Int) {}
    }

    companion object {
        const val ORIENTATION_HORIZONTAL: Int = 0
        const val ORIENTATION_VERTICAL: Int = 1
        const val OFFSCREEN_PAGE_LIMIT_DEFAULT: Int = -1
    }
}
