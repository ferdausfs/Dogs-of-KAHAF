// GATE STUB — ItemTouchHelper.
package androidx.recyclerview.widget

open class ItemTouchHelper(callback: Callback) {
    fun attachToRecyclerView(recyclerView: RecyclerView?) {}

    abstract class Callback {
        open fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = false

        abstract fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int)

        open fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int = 0
    }

    open class SimpleCallback(dragDirs: Int, swipeDirs: Int) : Callback() {
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }

    companion object {
        const val UP: Int = 1
        const val DOWN: Int = 2
        const val LEFT: Int = 4
        const val RIGHT: Int = 8
    }
}
