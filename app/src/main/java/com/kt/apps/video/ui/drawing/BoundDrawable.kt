package com.kt.apps.video.ui.drawing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

open class BoundDrawable(paint: Paint? = null) : Drawable() {
    open val paint = paint ?: Paint(Paint.ANTI_ALIAS_FLAG)
    open var isDrawingBound = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    override fun draw(canvas: Canvas) {
        if (isDrawingBound) {
            drawBound(canvas)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        if (paint.colorFilter != null) {
            return PixelFormat.TRANSLUCENT
        }

        return when (Color.alpha(paint.color)) {
            255 -> PixelFormat.OPAQUE
            0 -> PixelFormat.TRANSPARENT
            else -> PixelFormat.TRANSLUCENT
        }
    }

    protected open fun drawBound(canvas: Canvas) {
        drawBound(canvas, bounds)
    }

    protected fun drawBound(canvas: Canvas, bounds: Rect) {
        val pColor = paint.color
        val pStyle = paint.style
        val pStrokeWidth = paint.strokeWidth

        paint.color = 0xFF007AFF.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f

        canvas.drawRect(bounds, paint)

        paint.color = pColor
        paint.style = pStyle
        paint.strokeWidth = pStrokeWidth
    }
}
