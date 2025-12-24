package com.philkes.notallyx.presentation.view.note.ai

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Custom drawable để vẽ bubble với mũi nhọn chỉ lên trên
 */
class BubbleDrawable(
    private val cornerRadius: Float,
    private val arrowWidth: Float,
    private val arrowHeight: Float,
    private val arrowX: Float, // Vị trí X của mũi nhọn (tính từ trái)
    private val fillColor: Int = Color.WHITE,
    private val strokeColor: Int = 0xFFE0E0E0.toInt(),
    private val strokeWidth: Float = 1f
) : Drawable() {

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()

        // Vẽ bubble (hình chữ nhật bo góc) - bắt đầu từ dưới arrow
        val bubbleTop = arrowHeight
        val rect = RectF(
            0f,
            bubbleTop,
            width,
            height
        )
        path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

        // Vẽ mũi nhọn (triangle) chỉ lên trên
        val arrowCenterX = arrowX.coerceIn(arrowWidth / 2, width - arrowWidth / 2)
        val arrowPath = Path()
        arrowPath.moveTo(arrowCenterX - arrowWidth / 2, bubbleTop)
        arrowPath.lineTo(arrowCenterX, 0f)
        arrowPath.lineTo(arrowCenterX + arrowWidth / 2, bubbleTop)
        arrowPath.close()

        // Combine paths
        path.addPath(arrowPath)

        // Fill
        paint.color = fillColor
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)

        // Stroke
        paint.color = strokeColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        // Not needed
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        // Not needed
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

