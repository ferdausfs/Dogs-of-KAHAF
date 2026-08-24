// GATE STUB — com.google.android.material.snackbar.
package com.google.android.material.snackbar

import android.view.View

open class Snackbar private constructor() {

    open fun show() {}
    open fun dismiss() {}
    open fun setText(message: CharSequence): Snackbar = this
    open fun setText(resId: Int): Snackbar = this
    open fun setAction(resId: Int, listener: View.OnClickListener?): Snackbar = this
    open fun setAction(text: CharSequence?, listener: View.OnClickListener?): Snackbar = this
    open fun setDuration(duration: Int): Snackbar = this

    companion object {
        const val LENGTH_SHORT: Int = -1
        const val LENGTH_LONG: Int = 0
        const val LENGTH_INDEFINITE: Int = -2

        fun make(view: View, text: CharSequence, duration: Int): Snackbar = Snackbar()
        fun make(view: View, resId: Int, duration: Int): Snackbar = Snackbar()
    }
}
