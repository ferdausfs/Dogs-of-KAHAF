package com.guardian.shield.service.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import timber.log.Timber

/**
 * PreemptiveBlurManager — instantly draws an opaque overlay on top of
 * the current screen when a risky app opens, BEFORE AI inference completes.
 */
class PreemptiveBlurManager(
    private val service: AccessibilityService
) {
    companion object {
        private const val TAG                = "Guardian_Blur"
        private const val MAX_BLUR_DURATION_MS = 6_000L
    }

    private val wm: WindowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // Only accessed on Main thread — no synchronization needed
    private var overlayView: View?   = null
    private var currentPkg: String   = ""

    private val autoRemoveRunnable = Runnable {
        Timber.d("$TAG auto-remove (AI timeout)")
        removeOverlayOnMainThread()
    }

    fun showBlur(packageName: String, message: String = "Scanning content…") {
        mainHandler.post {
            if (overlayView != null && currentPkg == packageName) return@post
            if (overlayView != null) removeOverlayOnMainThread()

            currentPkg = packageName
            val view   = buildOverlayView(message)
            val params = buildLayoutParams()

            try {
                wm.addView(view, params)
                overlayView = view
                mainHandler.removeCallbacks(autoRemoveRunnable)
                mainHandler.postDelayed(autoRemoveRunnable, MAX_BLUR_DURATION_MS)
                Timber.d("$TAG showBlur: $packageName")
            } catch (e: Exception) {
                Timber.e(e, "$TAG showBlur failed")
                overlayView = null
            }
        }
    }

    fun hideBlur() {
        mainHandler.removeCallbacks(autoRemoveRunnable)
        mainHandler.post { removeOverlayOnMainThread() }
    }

    // FIX: All overlay operations on Main thread — no synchronized needed
    // FIX: @Synchronized + mainHandler.post deadlock risk removed
    private fun removeOverlayOnMainThread() {
        if (overlayView == null) return
        try {
            wm.removeViewImmediate(overlayView!!)
        } catch (e: Exception) {
            Timber.w(e, "$TAG removeView failed")
        } finally {
            overlayView = null
            currentPkg  = ""
        }
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post { removeOverlayOnMainThread() }
    }

    // ── Builders ───────────────────────────────────────────────────────

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type  = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        // FIX: FLAG_LAYOUT_INSET_DECOR removed — deprecated in API 30+
        val flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type, flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurBehindRadius = 60
                this.flags = this.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                dimAmount  = 0.85f
            } else {
                dimAmount = 1.0f
            }
        }
    }

    // FIX: dp conversion helper — prevents wrong sizes on different densities
    private fun Int.dp(): Int =
        (this * service.resources.displayMetrics.density).toInt()

    private fun buildOverlayView(message: String): View {
        val ctx  = service
        val root = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2000000"))
            }
            isClickable  = true
            isFocusable  = false
        }

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER }
        }

        val shield = TextView(ctx).apply {
            text     = "🛡️"
            textSize = 64f
            gravity  = Gravity.CENTER
        }
        val title = TextView(ctx).apply {
            text     = "Guardian Shield"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity  = Gravity.CENTER
            // FIX: dp() conversion — correct size on all screen densities
            setPadding(0, 16.dp(), 0, 0)
        }
        val subtitle = TextView(ctx).apply {
            text     = message
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 14f
            gravity  = Gravity.CENTER
            setPadding(32.dp(), 8.dp(), 32.dp(), 0)
        }
        val progress = ProgressBar(ctx).apply {
            val size = 96.dp()
            layoutParams = LinearLayout.LayoutParams(size, size).also {
                it.topMargin = 32.dp()
                it.gravity   = Gravity.CENTER
            }
            isIndeterminate = true
        }

        column.addView(shield)
        column.addView(title)
        column.addView(subtitle)
        column.addView(progress)
        root.addView(column)
        return root
    }
}