// GATE STUB — androidx.core.view (window-insets handling for edge-to-edge).
package androidx.core.view

import android.view.View

fun interface OnApplyWindowInsetsListener {
    fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat
}

object ViewCompat {
    fun setOnApplyWindowInsetsListener(view: View, listener: OnApplyWindowInsetsListener?) {}
    fun requestApplyInsets(view: View) {}
    fun getRootWindowInsets(view: View): WindowInsetsCompat? = null
}

class WindowInsetsCompat {

    fun getInsets(typeMask: Int): androidx.core.graphics.Insets =
        androidx.core.graphics.Insets.NONE

    class Type {
        companion object {
            fun statusBars(): Int = 1
            fun navigationBars(): Int = 2
            fun systemBars(): Int = 3
            fun displayCutout(): Int = 4
        }
    }

    companion object {
        val CONSUMED: WindowInsetsCompat = WindowInsetsCompat()
    }
}
