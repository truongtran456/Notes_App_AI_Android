package com.philkes.notallyx.common.ui.view.sliderview

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class CirclePointerRenderer(private val size: Float) {
    private val pointerBorderPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        color = Color.WHITE
        setShadowLayer(2f, 0f, 0f, Color.BLACK)
    }

    private val pointerBodyPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    fun drawPointer(canvas: Canvas, x: Float, y: Float, color: Int) {
        pointerBodyPaint.color = color
        canvas.drawCircle(x, y, size / 2f, pointerBorderPaint)
        canvas.drawCircle(x, y, size / 3f, pointerBodyPaint)
    }
}

