package com.starnest.common.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.withTranslation
import com.starnest.core.extension.px
import kotlin.math.abs
import kotlin.math.roundToInt

class OutlineTextViewV2 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    enum class GradientOrientation {
        HORIZONTAL, VERTICAL
    }

    private val outlinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val shadowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private var currentShader: Shader? = null

    //    val gradientColors = intArrayOf(
//        "#5C24D7".toColorInt(), "#E047DC".toColorInt(), "#ECA500".toColorInt(),
//        "#C94464".toColorInt(), "#659CDF".toColorInt(), "#2D53B5".toColorInt()
//    )
    private var customGradientColors: IntArray? = null
        set(value) {
            field = value
            currentShader = null
        }
    private var gradientOrientation = GradientOrientation.HORIZONTAL

    private var customStrokeWidth: Float = 0f
        set(value) {
            field = value
            outlinePaint.strokeWidth = value.roundToInt().px.toFloat()
        }

    private var customStrokeColor: Int = Color.WHITE
        set(value) {
            field = value
            outlinePaint.color = if (customStrokeWidth != 0f) {
                value
            } else Color.TRANSPARENT
        }

    private var customShadowDx: Float = 0f
        set(value) {
            field = value.roundToInt().px.toFloat()
        }

    private var customShadowDy: Float = 0f
        set(value) {
            field = value.roundToInt().px.toFloat()
        }

    private var customShadowColor: Int = Color.WHITE
        set(value) {
            field = value
            shadowPaint.color = value
        }

    private fun updatePadding() {
        val strokePad = if (customStrokeWidth != 0f) {
            (customStrokeWidth / 2).toInt() + 1
        } else 0
        val dx = abs(customShadowDx).toInt()
        val dy = abs(customShadowDy).toInt()
        setPadding(strokePad + dx, strokePad + dy, strokePad + dx, strokePad + dy)
        invalidate()
    }

    fun setup(
        strokeColor: Int = Color.WHITE,
        strokeWidth: Float = 0f,
        gradientColors: IntArray? = null,
        gradientOrientation: GradientOrientation = GradientOrientation.HORIZONTAL,
        shadowDx: Float = 0f,
        shadowDy: Float = 0f,
        shadowColor: Int = Color.WHITE
    ) {
        customStrokeWidth = strokeWidth
        customStrokeColor = strokeColor
        customGradientColors = gradientColors
        this.gradientOrientation = gradientOrientation
        customShadowDx = shadowDx
        customShadowDy = shadowDy
        customShadowColor = shadowColor

        updatePadding()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val layout = layout ?: return

        outlinePaint.textSize = textSize
        outlinePaint.typeface = typeface
        outlinePaint.textAlign = Paint.Align.LEFT

        shadowPaint.textSize = textSize
        shadowPaint.typeface = typeface

        // Vẽ màu gradient or màu của text
        val fillPaint = Paint(paint).apply {
            val gradientColors = customGradientColors
            if (gradientColors != null && gradientColors.size >= 2) {
                if (currentShader == null) {

                    val (x0, y0, x1, y1) = when (gradientOrientation) {
                        GradientOrientation.HORIZONTAL -> listOf(0f, 0f, width.toFloat(), 0f)
                        GradientOrientation.VERTICAL -> listOf(0f, 0f, 0f, height.toFloat())
                    }

                    currentShader = LinearGradient(
                        x0, y0, x1, y1,
                        gradientColors, null, Shader.TileMode.CLAMP
                    )
                }
                this.setShader(currentShader)
            } else {
                color = currentTextColor
            }
        }

        canvas.withTranslation(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat()) {
            for (i in 0 until layout.lineCount) {
                val start = layout.getLineStart(i)
                val end = layout.getLineEnd(i)
                val lineText = text?.subSequence(start, end).toString()

                val x = layout.getLineLeft(i)
                val y = layout.getLineBaseline(i).toFloat()

                // Vẽ shadow nếu có
                if (customShadowDx != 0f || customShadowDy != 0f) {
                    canvas.drawText(lineText, x + customShadowDx, y + customShadowDy, shadowPaint)
                }

                // Vẽ viền outline
                canvas.drawText(lineText, x, y, outlinePaint)

                // Vẽ text
                canvas.drawText(lineText, x, y, fillPaint)
            }
        }
    }
}