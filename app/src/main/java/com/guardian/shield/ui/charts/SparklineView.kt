package com.guardian.shield.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * R4 — REAL data-bound sparkline (replaces the static ic_sparkline art).
 * Fed with per-bucket counts (e.g. 24 hourly buckets of today's blocks).
 * Theme-agnostic: callers push colors via [setColors] so it blends onto the
 * current hero state (vivid violet / amber / red).
 */
class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var values: List<Int> = emptyList()
    private var lineColor = Color.WHITE

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePath = Path()
    private val areaPath = Path()

    fun setColors(line: Int) {
        lineColor = line
        linePaint.color = line
        invalidate()
    }

    fun setData(data: List<Int>) {
        values = data
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        linePaint.strokeWidth = 2.2f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom
        if (w <= 0 || h <= 0) return

        val n = if (values.isEmpty()) 2 else values.size
        val maxV = maxOf(1, values.maxOrNull() ?: 0)
        fun valueAt(i: Int): Int =
            if (values.isEmpty()) 0 else values[i.coerceAtMost(values.lastIndex)]

        val minY = paddingTop + h * 0.10f
        val maxY = paddingTop + h * 0.88f
        fun yOf(v: Int): Float =
            if (v == 0) paddingTop + h * 0.74f
            else maxY - (v.toFloat() / maxV) * (maxY - minY)

        val step = w.toFloat() / (n - 1).coerceAtLeast(1)
        val xs = FloatArray(n) { paddingLeft + it * step }
        val ys = FloatArray(n) { yOf(valueAt(it)) }

        fun buildSmooth(path: Path) {
            path.reset()
            path.moveTo(xs[0], ys[0])
            for (i in 1 until n) {
                // Smooth through midpoints of the REAL samples — visually
                // gentle, but every sample point remains on the curve.
                val midX = (xs[i - 1] + xs[i]) / 2f
                val midY = (ys[i - 1] + ys[i]) / 2f
                path.quadTo(xs[i - 1], ys[i - 1], midX, midY)
            }
            path.lineTo(xs[n - 1], ys[n - 1])
        }

        buildSmooth(areaPath)
        val bottom = (height - paddingBottom).toFloat()
        areaPath.lineTo(xs[n - 1], bottom)
        areaPath.lineTo(xs[0], bottom)
        areaPath.close()

        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, bottom,
            withAlpha(lineColor, 0.24f), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawPath(areaPath, fillPaint)

        buildSmooth(linePath)
        canvas.drawPath(linePath, linePaint)
    }

    private fun withAlpha(color: Int, a: Float): Int =
        Color.argb((a * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
