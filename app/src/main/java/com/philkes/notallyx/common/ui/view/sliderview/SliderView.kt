package com.philkes.notallyx.common.ui.view.sliderview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import com.philkes.notallyx.R
import com.philkes.notallyx.common.extension.dp

class SliderView(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {

    interface SliderViewListener {
        fun onProgressChanged(value: Float)
    }

    private val sliderPadding: Float = 2.dp.toFloat()
    private var sliderRenderer: CirclePointerRenderer? = null

    private var trackRounded: Float = 0f
    private val trackMovableArea = RectF()
    private val trackArea = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 0.5f
        color = "#F8F2ED".toColorInt()
    }

    private var firstGradientColor: String = "#00FFFFFF"
    private var secondGradientColor: String = "#FF000000"
    private var pointerColor = "#000000"

    private var alphaTileShader: BitmapShader? = null
    private var gradientShader: LinearGradient? = null

    private var progress: Float = 0f
    private var isDragging = false
    private var isOpacitySlider = false

    var sliderViewListener: SliderViewListener? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)

        val x = restrictPointerX(event.x)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                isDragging = true
                progress = calculateProgress(x)
                sliderViewListener?.onProgressChanged(progress)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawTrack(canvas)
        drawSlider(canvas)
    }

    private fun drawSlider(canvas: Canvas) {
        val x = calculatePointerX(progress)
        val y = trackMovableArea.centerY()
        sliderRenderer?.drawPointer(canvas, x, y, pointerColor.toColorInt())
    }

    private fun drawTrack(canvas: Canvas) {
        val pointerX = calculatePointerX(progress)
        canvas.drawRoundRect(trackArea, trackRounded, trackRounded, borderPaint)
        val filledRect = RectF(trackArea.left, trackArea.top, pointerX, trackArea.bottom)
        if (alphaTileShader != null) {
            trackPaint.shader = alphaTileShader
            canvas.drawRoundRect(trackArea, trackRounded, trackRounded, trackPaint)
        }
        trackPaint.shader = gradientShader
        canvas.drawRoundRect(filledRect, trackRounded, trackRounded, trackPaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val sliderHeight = h - sliderPadding * 2
        trackRounded = sliderHeight

        trackArea.apply {
            set(0f, 0f, w.toFloat(), h.toFloat())
            inset(sliderPadding, sliderPadding)

            val trackHeight = height() / 1.5f
            val verticalPadding = (height() - trackHeight) / 2
            inset(0f, verticalPadding)
        }

        trackMovableArea.apply {
            val restrictedLeft = trackArea.left + sliderHeight / 2
            val restrictedTop = trackArea.top
            val restrictedRight = trackArea.right - sliderHeight / 2
            val restrictedBottom = trackArea.bottom
            set(restrictedLeft, restrictedTop, restrictedRight, restrictedBottom)
        }

        sliderRenderer = CirclePointerRenderer(sliderHeight)
        gradientShader = createGradientShader(trackArea, firstGradientColor, secondGradientColor)
        alphaTileShader = if (isOpacitySlider) {
            createAlphaTileShader(trackArea)
        } else {
            null
        }
    }

    fun setIsOpacitySlider(isOpacity: Boolean) {
        isOpacitySlider = isOpacity
        invalidate()
    }

    private fun createGradientShader(
        rect: RectF,
        firstColor: String,
        secondColor: String
    ): LinearGradient {
        return LinearGradient(
            rect.left / 2f,
            rect.left / 2f,
            rect.right / 2f,
            rect.right / 2f,
            firstColor.toColorInt(),
            secondColor.toColorInt(),
            Shader.TileMode.CLAMP
        )
    }

    private fun createAlphaTileShader(rect: RectF): BitmapShader {
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.bg_tile_light)
        val bitmapSize = rect.height().toInt() / 3
        val scaledBitmap = bitmap.scale(bitmapSize, bitmapSize, false)
        return BitmapShader(scaledBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private fun restrictPointerX(pointerX: Float): Float {
        return when {
            pointerX < trackMovableArea.left -> trackMovableArea.left
            pointerX > trackMovableArea.right -> trackMovableArea.right
            else -> pointerX
        }
    }

    private fun calculateProgress(x: Float): Float {
        val max = trackMovableArea.right - trackMovableArea.left
        val value = x - trackMovableArea.left

        return value / max
    }

    private fun calculatePointerX(progress: Float): Float {
        return progress * (trackMovableArea.right - trackMovableArea.left) + trackMovableArea.left
    }

    fun setProgress(value: Float) {
        if (!isDragging) {
            progress = value
            invalidate()
        }
    }

    fun getProgress(): Float {
        return progress
    }

    fun setCurrentColor(color: String) {
        pointerColor = color
        invalidate()
    }

    fun setFirstGradientColor(color: String) {
        firstGradientColor = color
        gradientShader = createGradientShader(
            rect = trackArea,
            firstColor = firstGradientColor,
            secondColor = secondGradientColor
        )
        invalidate()
    }

    fun setSecondGradientColor(color: String) {
        secondGradientColor = color
        gradientShader = createGradientShader(
            rect = trackArea,
            firstColor = firstGradientColor,
            secondColor = secondGradientColor
        )
        invalidate()
    }
}

