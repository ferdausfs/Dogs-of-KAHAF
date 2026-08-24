package com.guardian.shield.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Edge-to-edge inset helper (v3.6.7 UI FIX).
 *
 * targetSdk 35 makes edge-to-edge MANDATORY on Android 15+: every activity
 * now draws behind the status bar, and because the app never consumed window
 * insets anywhere, each screen's top app bar slid up underneath it — the
 * "back button floating way too high" regression seen on owner's device.
 *
 * These helpers apply SELECTIVE padding (top-only / bottom-only) instead of
 * the legacy `fitsSystemWindows="true"`, because fitsSystemWindows pads ALL
 * four sides and would also inject the navigation-bar inset into toolbars
 * (huge dead gap inside the app bar) and the status-bar inset into the bottom
 * navigation. Base padding is captured once so repeated inset dispatches
 * (rotation, cutout changes) never double-pad.
 */
object ScreenInsets {

    /** Top app bars / full-screen headers: lift content below the status bar. */
    fun padTopForStatusBar(target: View) {
        val baseTop = target.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(target) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, baseTop + top, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(target)
    }

    /** Bottom navigation: keep items above the gesture/nav bar. */
    fun padBottomForNavBar(target: View) {
        val baseBottom = target.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(target) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, baseBottom + bottom)
            insets
        }
        ViewCompat.requestApplyInsets(target)
    }
}
