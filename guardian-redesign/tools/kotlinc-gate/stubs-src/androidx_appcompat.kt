// GATE STUB — androidx.appcompat.
package androidx.appcompat.app

import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.Button

open class ActionBar {
    open fun setDisplayHomeAsUpEnabled(showHomeAsUp: Boolean) {}
    open fun setTitle(resId: Int) {}
    open fun setTitle(title: CharSequence?) {}
    open fun setSubtitle(subtitle: CharSequence?) {}
    open fun setDisplayShowTitleEnabled(showTitle: Boolean) {}
    open fun hide() {}
    open fun show() {}
}

abstract class AppCompatActivity : androidx.fragment.app.FragmentActivity {
    constructor() : super()
    open val supportActionBar: ActionBar? get() = null
    open fun setSupportActionBar(toolbar: androidx.appcompat.widget.Toolbar?) {}
    override fun invalidateOptionsMenu() {}
}

open class AlertDialog(context: Context) : android.app.Dialog(context) {
    override fun dismiss() {}
    fun getButton(whichButton: Int): Button? = null

    open class Builder(protected val context: Context) {
        open fun setTitle(titleId: Int): Builder = this
        open fun setTitle(title: CharSequence?): Builder = this
        open fun setMessage(messageId: Int): Builder = this
        open fun setMessage(message: CharSequence?): Builder = this
        open fun setView(view: View?): Builder = this
        open fun setPositiveButton(textId: Int, listener: DialogInterface.OnClickListener?): Builder = this
        open fun setPositiveButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder = this
        open fun setNegativeButton(textId: Int, listener: DialogInterface.OnClickListener?): Builder = this
        open fun setNegativeButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder = this
        open fun setNeutralButton(textId: Int, listener: DialogInterface.OnClickListener?): Builder = this
        open fun setNeutralButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder = this
        open fun setCancelable(cancelable: Boolean): Builder = this
        open fun setOnDismissListener(listener: DialogInterface.OnDismissListener?): Builder = this
        open fun create(): AlertDialog = AlertDialog(context)
        open fun show(): AlertDialog = AlertDialog(context)
    }
}

object AppCompatDelegate {
    const val MODE_NIGHT_FOLLOW_SYSTEM: Int = -1
    const val MODE_NIGHT_NO: Int = 1
    const val MODE_NIGHT_YES: Int = 2
    const val MODE_NIGHT_AUTO_BATTERY: Int = 3
    const val MODE_NIGHT_UNSPECIFIED: Int = -100
    fun setDefaultNightMode(mode: Int) {}
}
