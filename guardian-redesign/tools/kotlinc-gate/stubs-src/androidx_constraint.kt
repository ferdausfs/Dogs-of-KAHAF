// GATE STUB — constraintlayout (roots/ids in layouts).
package androidx.constraintlayout.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup

open class ConstraintLayout(context: Context) : ViewGroup(context) {
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
}

open class Guideline(context: Context) : View(context)

open class Barrier(context: Context) : View(context)

open class Group(context: Context) : View(context)
