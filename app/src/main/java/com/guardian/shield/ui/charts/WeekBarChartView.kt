package com.guardian.shield.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * R4 — REAL data-bound weekly bar chart (replaces static ic_bars_week art on
 * the Reports card). 7 buckets, Monday-first to match the M..S letter row in
 * the layout. Zero-count days render as short track stubs so the week shape
 * is always visible.
 */
class WeekBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var values: List<Int> = List(7) { 0 }
    private var barColor = Color.WHITE
    private var trackColor = Color.argb(90, 255, 255, 255)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    fun setColors(bar: Int, track: Int) {
        barColor = bar
        trackColor = track
        invalidate()
    }

    fun setData(data: List<Int>) {
        values = if (data.isEmpty()) List(7) { 0 } else data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        barPaint.color = barColor
        trackPaint.color = trackColor

        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom
        if (w <= 0 || h <= 0) return

        val n = values.size
        val slotW = w.toFloat() / n
        val barW = slotW * 0.52f
        val maxV = maxOf(1, values.maxOrNull() ?: 0)
        val bottom = paddingTop + h.toFloat()
        val stubH = 3.5f * resources.displayMetrics.density
        val minBarH = 8f * resources.displayMetrics.density

        for (i in 0 until n) {
            val v = values[i]
            val left = paddingLeft + i * slotW + (slotW - barW) / 2f
            val barH: Float
            val paint: Paint
            if (v <= 0) {
                barH = stubH
                paint = trackPaint
            } else {
                barH = maxOf(minBarH, (v.toFloat() / maxV) * h)
                paint = barPaint
            }
            val top = bottom - barH
            val radius = minOf(barW / 2f, maxOf(2f, barH / 2f))
            rect.set(left, top, left + barW, bottom)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}
