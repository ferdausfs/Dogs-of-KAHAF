// GATE STUB — androidx.appcompat.widget.
package androidx.appcompat.widget

import android.content.Context

open class Toolbar(context: Context) : android.view.ViewGroup(context) {
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {}
    var title: CharSequence?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") v) {}
    fun setTitle(resId: Int) {}
    fun setSubtitle(subtitle: CharSequence?) {}
    fun setNavigationOnClickListener(listener: android.view.View.OnClickListener?) {}
}

open class AppCompatButton(context: Context) : android.widget.Button(context)

open class SwitchCompat(context: Context) : android.widget.CompoundButton(context)
