// GATE STUB — com.google.android.material.bottomsheet.
package com.google.android.material.bottomsheet

import android.content.Context
import androidx.fragment.app.DialogFragment

open class BottomSheetDialogFragment : DialogFragment {
    constructor() : super()
}

open class BottomSheetDialog(context: Context) : android.app.Dialog(context) {
    override fun dismiss() {}
}
