// GATE STUB — com.google.android.material.slider.
package com.google.android.material.slider

import android.content.Context
import android.view.View

open class Slider(context: Context) : View(context) {

    fun interface OnChangeListener {
        fun onValueChange(slider: Slider, value: Float, fromUser: Boolean)
    }

    var value: Float = 0f
    var valueFrom: Float = 0f
    var valueTo: Float = 100f
    var stepSize: Float = 0f

    open fun addOnChangeListener(listener: OnChangeListener) {}
    open fun removeOnChangeListener(listener: OnChangeListener) {}
    open fun clearOnChangeListeners() {}
}
