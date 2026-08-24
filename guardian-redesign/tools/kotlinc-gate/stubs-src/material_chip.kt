// GATE STUB — com.google.android.material.chip.
package com.google.android.material.chip

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.ViewGroup

open class Chip(context: Context) : android.widget.CheckBox(context) {
    var isCheckable: Boolean = true
    var chipIcon: Drawable? = null
    var isCheckedIconVisible: Boolean = true
    var chipStrokeWidth: Float = 0f
    var chipStrokeColor: Int = 0
    var chipBackgroundColor: Int = 0
}

open class ChipGroup(context: Context) : ViewGroup(context) {

    fun interface OnCheckedStateChangeListener {
        fun onCheckedChanged(group: ChipGroup, checkedIds: List<Int>)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
    open fun setOnCheckedStateChangeListener(listener: OnCheckedStateChangeListener?) {}
    open fun check(id: Int) {}
    open fun clearCheck() {}
    open val checkedChipId: Int
        get() = -1
    open val checkedChipIds: List<Int>
        get() = emptyList()
}
