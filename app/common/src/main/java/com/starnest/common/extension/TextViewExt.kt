package com.starnest.common.extension

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

fun TextView.setGradientTextColor(
    angle: Double = 45.0,
    colors: List<Int>,
) {
    post {
        try {
            val textBound = Rect(Int.MAX_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)

            for (i in 0 until lineCount) {
                val left: Float = layout.getLineLeft(i)
                val right: Float = layout.getLineRight(i)
                if (left < textBound.left) textBound.left = left.toInt()
                if (right > textBound.right) textBound.right = right.toInt()
            }

            textBound.top = layout.getLineTop(0)
            textBound.bottom = layout.getLineBottom(lineCount - 1)

            if (includeFontPadding) {
                val fontMetrics: Paint.FontMetrics = paint.fontMetrics
                textBound.top = (textBound.top + (fontMetrics.ascent - fontMetrics.top)).toInt()
                textBound.bottom =
                    (textBound.bottom - (fontMetrics.bottom - fontMetrics.descent)).toInt()
            }

            val angleInRadians = Math.toRadians(angle)

            val r = sqrt(
                (textBound.bottom - textBound.top).toFloat().pow(2) +
                        (textBound.right - textBound.left).toFloat().pow(2)
            ) / 2

            val centerX = (textBound.left + (textBound.right - textBound.left) / 2).toFloat()
            val centerY = (textBound.top + (textBound.bottom - textBound.top) / 2).toFloat()

            val startX = max(
                textBound.left.toDouble(),
                min(textBound.right.toDouble(), centerX - r * cos(angleInRadians))
            ).toFloat()

            val startY = min(
                textBound.bottom.toDouble(),
                max(textBound.top.toDouble(), centerY - r * sin(angleInRadians))
            ).toFloat()

            val endX = max(
                textBound.left.toDouble(),
                min(textBound.right.toDouble(), centerX + r * cos(angleInRadians))
            ).toFloat()

            val endY = min(
                textBound.bottom.toDouble(),
                max(textBound.top.toDouble(), centerY + r * sin(angleInRadians))
            ).toFloat()

            val textShader = LinearGradient(
                startX,
                startY,
                endX,
                endY,
                colors.toIntArray(),
                null,
                Shader.TileMode.CLAMP
            )

            paint.shader = textShader
            colors.firstOrNull()?.let(::setTextColor)
        } catch (e: Exception) {
            colors.firstOrNull()?.let(::setTextColor)
            e.printStackTrace()
        }
    }
}


fun TextView.setTextColorGradient(
    colors: IntArray,
    angle: Float = 90f
) {
    setTextColor(colors[0])
    val typeface = this.typeface
    val textSize = this.textSize
    post {
        val radians = Math.toRadians(angle.toDouble())
        val width = this.width.toFloat()
        val height = this.height.toFloat()

        val x0 = 0f
        val y0 = 0f
        val x1 = (Math.cos(radians) * width).toFloat()
        val y1 = (Math.sin(radians) * height).toFloat()

        val shader = LinearGradient(
            x0,
            y0,
            x1,
            y1,
            colors,
            null,
            Shader.TileMode.CLAMP
        )

        this.paint.typeface = typeface
        this.paint.textSize = textSize
        this.paint.shader = shader
        invalidate()
    }
}

fun TextView.underline() {
    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
}

