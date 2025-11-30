package com.starnest.common.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.graphics.createBitmap
import androidx.core.view.isEmpty
import com.starnest.core.extension.color
import com.starnest.resources.R
import kotlin.apply
import kotlin.let

open class ShadowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var dx: Int = 0
    private var dy: Int = 0

    private var savedDx: Int = 0
    private var savedDy: Int = 0

    var shadowColor: Int = "#FFD2AA".color
        set(value) {
            field = value

            shadowMask?.setTint(value)


            invalidate()
        }
    private var shadowMask: Drawable? = null
    private var shadowBitmap: Bitmap? = null
    private var shadowCanvas: Canvas? = null

    var isShadowEnabled = true
        set(value) {
            field = value
            if (value) {
                dx = savedDx
                dy = savedDy
            } else {
                savedDx = dx
                savedDy = dy
                dx = 0
                dy = 0
            }
            updatePadding()
            invalidate()
        }

    init {
        attrs?.let { initAttr(attrs) }
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun initAttr(attrs: AttributeSet) {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.ShadowView, 0, 0)
        try {
            dx = a.getDimension(R.styleable.ShadowView_sv_dx, 0f).toInt()
            dy = a.getDimension(R.styleable.ShadowView_sv_dy, 0f).toInt()
            savedDx = dx
            savedDy = dy
            shadowColor = a.getColor(R.styleable.ShadowView_sv_color, "#FFD2AA".color)
            shadowMask = a.getDrawable(R.styleable.ShadowView_sv_mask)?.mutate()
            shadowMask?.setTint(shadowColor)

            updatePadding()
        } finally {
            a.recycle()
        }
    }

    private fun updatePadding() {
        val leftPadding = dx
        val bottomPadding = dy

        setPadding(leftPadding, paddingTop, paddingRight, bottomPadding)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (shadowMask == null || w <= 0 || h <= 0) {
            return
        }

        shadowBitmap?.recycle()
        shadowBitmap = createBitmap(w, h)
        shadowCanvas = Canvas(shadowBitmap!!)
    }

    fun configShadow(dx: Int, dy: Int, shadowMask: Drawable?, color: Int) {
        this.dx = dx
        this.dy = dy
        this.shadowMask = shadowMask
        this.shadowColor = shadowColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (isEmpty() || !isShadowEnabled) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            return super.onDraw(canvas)
        }

        val child = getChildAt(0)
        shadowBitmap?.let { bitmap ->
            // Clear previous shadow
            shadowCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            // Calculate shadow bounds
            val left = 0
            val top = dy
            val right = left + child.width
            val bottom = top + child.height

            // Draw shadow mask
            shadowMask?.apply {
                setBounds(left, top, right, bottom)
                draw(shadowCanvas!!)
            }

            // Draw the shadow bitmap
            canvas.drawBitmap(bitmap, 0f, 0f, shadowPaint)
        }

        // Draw the content
        super.onDraw(canvas)
    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (shadowMask == null || width <= 0 || height <= 0) {
            return
        }

        shadowBitmap?.recycle()
        shadowBitmap = createBitmap(width, height)
        shadowCanvas = Canvas(shadowBitmap!!)
    }


    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        shadowBitmap?.recycle()
        shadowBitmap = null
        shadowCanvas = null
    }
}