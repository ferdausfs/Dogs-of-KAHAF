// GATE STUB — com.google.android.material.card.
package com.google.android.material.card

import android.content.Context
import android.widget.FrameLayout

open class MaterialCardView(context: Context) : FrameLayout(context) {
    open fun setCardBackgroundColor(color: Int) {}
    var radius: Float = 0f
    var strokeColor: Int = 0
    var strokeWidth: Int = 0
    var cardElevation: Float = 0f
    var isChecked: Boolean = false
}
