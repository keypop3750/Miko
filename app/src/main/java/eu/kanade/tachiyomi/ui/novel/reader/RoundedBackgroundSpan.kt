package eu.kanade.tachiyomi.ui.novel.reader

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan
import kotlin.math.roundToInt

/**
 * A span that draws a rounded rectangle background behind text.
 * Uses top and bottom padding for visual tweaking.
 */
class RoundedBackgroundSpan(
    private val backgroundColor: Int,
    private val cornerRadiusPx: Float = 8f,
    private val paddingTopPx: Int = 6,
    private val paddingBottomPx: Int = 2,
    private val paddingHorizontalPx: Int = 4,
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val width = paint.measureText(text, start, end).roundToInt()
        if (fm != null) {
            fm.top -= paddingTopPx
            fm.ascent -= paddingTopPx
            fm.bottom += paddingBottomPx
            fm.descent += paddingBottomPx
        }
        return width + paddingHorizontalPx * 2
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val width = paint.measureText(text, start, end)
        val rect = RectF(
            x,
            top.toFloat(),
            x + width + paddingHorizontalPx * 2,
            bottom.toFloat()
        )
        val bgPaint = Paint(paint).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, bgPaint)

        // Draw text on top
        val textPaint = Paint(paint).apply {
            color = paint.color
        }
        canvas.drawText(text, start, end, x + paddingHorizontalPx, y.toFloat(), textPaint)
    }
}
