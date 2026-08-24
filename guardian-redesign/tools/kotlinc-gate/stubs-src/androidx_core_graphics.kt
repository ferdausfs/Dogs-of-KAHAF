// GATE STUB — androidx.core.graphics.
package androidx.core.graphics

class Insets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    companion object {
        val NONE: Insets = Insets(0, 0, 0, 0)

        fun of(left: Int, top: Int, right: Int, bottom: Int): Insets =
            Insets(left, top, right, bottom)
    }
}
