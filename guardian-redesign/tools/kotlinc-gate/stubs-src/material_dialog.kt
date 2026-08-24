// GATE STUB — com.google.android.material.dialog.
package com.google.android.material.dialog

import android.content.Context
import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog

open class MaterialAlertDialogBuilder(protected val context: Context) {
    open fun setTitle(titleId: Int): MaterialAlertDialogBuilder = this
    open fun setTitle(title: CharSequence?): MaterialAlertDialogBuilder = this
    open fun setMessage(messageId: Int): MaterialAlertDialogBuilder = this
    open fun setMessage(message: CharSequence?): MaterialAlertDialogBuilder = this
    open fun setView(view: View?): MaterialAlertDialogBuilder = this
    open fun setIcon(iconId: Int): MaterialAlertDialogBuilder = this
    open fun setPositiveButton(
        textId: Int,
        listener: DialogInterface.OnClickListener?
    ): MaterialAlertDialogBuilder = this

    open fun setPositiveButton(
        text: CharSequence?,
        listener: DialogInterface.OnClickListener?
    ): MaterialAlertDialogBuilder = this

    open fun setNegativeButton(
        textId: Int,
        listener: DialogInterface.OnClickListener?
    ): MaterialAlertDialogBuilder = this

    open fun setNegativeButton(
        text: CharSequence?,
        listener: DialogInterface.OnClickListener?
    ): MaterialAlertDialogBuilder = this

    open fun setNeutralButton(
        textId: Int,
        listener: DialogInterface.OnClickListener?
    ): MaterialAlertDialogBuilder = this

    open fun setNeutralButton(
        text: CharSequence?,
        listener: DialogInterface.OnClickListener?
    ): MaterialAlertDialogBuilder = this

    open fun setCancelable(cancelable: Boolean): MaterialAlertDialogBuilder = this
    open fun setOnDismissListener(
        listener: DialogInterface.OnDismissListener?
    ): MaterialAlertDialogBuilder = this

    open fun setItems(
        itemsId: Int,
        listener: DialogInterface.OnClickListener?
    ): MaterialAlertDialogBuilder = this

    open fun create(): AlertDialog = AlertDialog(context)
    open fun show(): AlertDialog = AlertDialog(context)
}
