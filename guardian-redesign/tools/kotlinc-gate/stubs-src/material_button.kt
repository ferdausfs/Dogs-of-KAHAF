// GATE STUB — com.google.android.material.button.
package com.google.android.material.button

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton

open class MaterialButton(context: Context) : AppCompatButton(context) {
    var icon: Drawable? = null
    var cornerRadius: Int = 0
    var strokeWidth: Int = 0
}

open class MaterialButtonToggleGroup(context: Context) : LinearLayout(context) {

    fun interface OnButtonCheckedListener {
        fun onButtonChecked(group: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean)
    }

    open fun addOnButtonCheckedListener(listener: OnButtonCheckedListener) {}
    open fun check(id: Int) {}
    open fun uncheck(id: Int) {}
    open fun clearChecked() {}
    open val checkedButtonId: Int
        get() = -1
}
