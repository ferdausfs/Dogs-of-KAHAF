package com.guardian.shield.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * R4 — Focus Mode countdown ring. [setProgress] takes the ELAPSED fraction
 * (0f fresh session → 1f finished). Colors are pushed by the caller so the
 * ring inherits the hero's on-color.
 */
class FocusRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f
    private var ringColor = Color.WHITE
    private var trackColor = Color.argb(70, 255, 255, 255)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bounds = RectF()

    fun setProgress(fraction: Float) {
        progress = fraction.coerceIn(0f, 1f)
        invalidate()
    }

    fun setColors(ring: Int, track: Int) {
        ringColor = ring
        trackColor = track
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val stroke = 6.5f * resources.displayMetrics.density
        ringPaint.strokeWidth = stroke
        trackPaint.strokeWidth = stroke
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ringPaint.color = ringColor
        trackPaint.color = trackColor
        val inset = ringPaint.strokeWidth / 2f + 1f
        bounds.set(
            paddingLeft + inset, paddingTop + inset,
            width - paddingRight - inset, height - paddingBottom - inset
        )
        canvas.drawArc(bounds, 0f, 360f, false, trackPaint)
        val sweep = 360f * progress
        if (sweep > 0.01f) {
            canvas.drawArc(bounds, -90f, sweep, false, ringPaint)
        }
    }
}
