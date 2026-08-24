// GATE STUB — com.google.android.material.textfield.
package com.google.android.material.textfield

import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout

open class TextInputLayout(context: Context) : LinearLayout(context) {
    var error: CharSequence? = null
    var hint: CharSequence? = null
    var isErrorEnabled: Boolean = false
    var isHintEnabled: Boolean = true
    var counterMaxLength: Int = 0
    open val editText: EditText?
        get() = null
}

open class TextInputEditText(context: Context) : EditText(context)
