// GATE STUB — androidx.core.
package androidx.core.content

import android.content.Context
import android.graphics.drawable.Drawable

object ContextCompat {
    fun getColor(context: Context, id: Int): Int = 0
    fun getDrawable(context: Context, id: Int): Drawable? = null
    fun checkSelfPermission(context: Context, permission: String): Int = 0
    fun getColorStateList(context: Context, id: Int): android.content.res.ColorStateList? = null
    fun startForegroundService(context: Context, intent: android.content.Intent) {}
    fun getSystemService(context: Context, serviceClass: Class<*>?): Any? = null
}
