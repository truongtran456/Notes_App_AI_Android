package com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.view.View
import com.philkes.notallyx.common.extension.px
import com.philkes.notallyx.common.model.Brush
import com.philkes.notallyx.common.model.BrushGroup
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class DrawToolPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val curvedPath = Path()
    private val pathPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapCache = mutableMapOf<Int, Bitmap>()

    var padding: Int = 40.px
    var mColor: Int = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }
    var brush: Brush = Brush.Calligraphy
        set(value) {
            field = value
            invalidate()
        }
    var mStrokeWidth: Float = 10f
        set(value) {
            field = value
            updateCurvedPath()
            invalidate()
        }

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCurvedPath()
    }

    private fun updateCurvedPath() {
        if (width == 0 || height == 0) {
            return
        }
        curvedPath.reset()
        val left = mStrokeWidth / 2 + padding
        val right = width - mStrokeWidth / 2 - padding
        val top = mStrokeWidth / 2 + padding
        val bottom = height - mStrokeWidth / 2 - padding
        curvedPath.moveTo(left, bottom)
        curvedPath.cubicTo(
            width / 4f,
            -height * 2f / 3f + mStrokeWidth,
            width * 2f / 3f,
            height * 2f - height / 4f - mStrokeWidth,
            right,
            top,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (brush.group == BrushGroup.Pen3D || brush.stampRes != 0) {
            drawStampPath(canvas)
        } else {
            drawPaintPath(canvas)
        }
    }

    private fun drawPaintPath(canvas: Canvas) {
        pathPaint.apply {
            color = mColor
            strokeWidth = max(4f, mStrokeWidth)
            strokeCap = if (brush == Brush.Calligraphy) Paint.Cap.SQUARE else Paint.Cap.ROUND
        }
        canvas.drawPath(curvedPath, pathPaint)
    }

    private fun drawStampPath(canvas: Canvas) {
        val stampRes = brush.stampRes
        if (stampRes == 0) {
            drawPaintPath(canvas)
            return
        }
        val baseBitmap =
            bitmapCache.getOrPut(stampRes) {
                BitmapFactory.decodeResource(resources, stampRes)
            } ?: return

        val size = max(4, mStrokeWidth.toInt())
        val scaled = Bitmap.createScaledBitmap(baseBitmap, size, size, false)
        if (brush != Brush.Strawberry3D) {
            stampPaint.colorFilter = PorterDuffColorFilter(mColor, PorterDuff.Mode.SRC_IN)
        } else {
            stampPaint.colorFilter = null
        }

        val samples = max(1, min(5000, (5000f / mStrokeWidth).toInt()))
        val pm = PathMeasure(curvedPath, false)
        val coordinates = FloatArray(2)
        for (i in 1..samples) {
            pm.getPosTan(pm.length * i / samples, coordinates, null)
            val jitter =
                if (brush == Brush.Pencil) {
                    Random.nextFloat() * 6 - 3f
                } else {
                    0f
                }
            canvas.drawBitmap(
                scaled,
                coordinates[0] - scaled.width / 2f + jitter,
                coordinates[1] - scaled.height / 2f + jitter,
                stampPaint,
            )
        }
    }
}

