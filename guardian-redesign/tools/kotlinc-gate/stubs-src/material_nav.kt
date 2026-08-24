// GATE STUB — com.google.android.material.bottomnavigation.
package com.google.android.material.bottomnavigation

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout

open class BottomNavigationView(context: Context) : FrameLayout(context) {

    fun interface OnItemSelectedListener {
        fun onNavigationItemSelected(item: MenuItem): Boolean
    }

    fun interface OnItemReselectedListener {
        fun onNavigationItemReselected(item: MenuItem)
    }

    open fun setOnItemSelectedListener(listener: OnItemSelectedListener?) {}
    open fun setOnItemReselectedListener(listener: OnItemReselectedListener?) {}

    var selectedItemId: Int = 0

    val menu: Menu
        get() = throw RuntimeException("stub")

    val maxItemCount: Int
        get() = 5
}
