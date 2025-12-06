package com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.PopupWindow
import androidx.core.graphics.toColorInt
import com.philkes.notallyx.BR
import com.philkes.notallyx.R
import com.philkes.notallyx.common.extension.adjustAlpha
import com.philkes.notallyx.common.extension.debounceClick
import com.philkes.notallyx.common.extension.gone
import com.philkes.notallyx.common.extension.isPhoneLandscape
import com.philkes.notallyx.common.extension.isPhonePortrait
import com.philkes.notallyx.common.extension.isTabletLandscape
import com.philkes.notallyx.common.extension.isTabletPortrait
import com.philkes.notallyx.common.extension.px
import com.philkes.notallyx.common.extension.screenSize
import com.philkes.notallyx.common.model.Brush
import com.philkes.notallyx.common.model.BrushGroup
import com.philkes.notallyx.common.model.DrawToolBrush
import com.philkes.notallyx.common.model.DrawToolPenType
import com.philkes.notallyx.common.ui.view.colorview.ColorPickerItem
import com.philkes.notallyx.common.ui.view.sliderview.SliderView
import com.philkes.notallyx.core.ui.widget.AbstractView
import com.philkes.notallyx.databinding.ItemDrawToolConfigViewBinding
import com.philkes.notallyx.draw.ui.newdraw.view.drawnumberpickerview.DrawNumberPickerView
import com.philkes.notallyx.draw.ui.newdraw.view.drawtool.DrawToolData
import com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig.colorpicker.ColorPaletteOption
import com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig.colorpicker.ColorPaletteView
import com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig.colorpicker.ColorType
import com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig.drawtool.DrawToolBrushGalleryView

enum class ViewType {
    SIZE, OPACITY, STREAMLINE
}

class DrawToolConfigView(
    context: Context,
    attrs: AttributeSet?,
) : AbstractView(context, attrs) {

    interface DrawToolConfigListener {
        fun onChange(drawToolBrush: DrawToolBrush)
    }

    override fun layoutId(): Int = R.layout.item_draw_tool_config_view

    override fun viewBinding(): ItemDrawToolConfigViewBinding {
        return binding as ItemDrawToolConfigViewBinding
    }

    override fun viewInitialized() {
        post {
            setupUI()
            setupAction()
        }
    }

    var drawToolBrush: DrawToolBrush? = null
        set(value) {
            field = value

            if (value != null && isViewInitialized) {
                setupPreview()
                updateEraser()
            }
        }

    var listener: DrawToolConfigListener? = null

    private var currentSize: Float = 5f
        set(value) {
            field = value

            if (isViewInitialized) {
                viewBinding().etSize.text = value.toInt().toString()
                viewBinding().sliderSizeView.setProgress(value / 100)

                changeData {
                    it.sliderSize = value
                }
            }
        }
    private var currentOpacity: Float = 1f
        set(value) {
            field = value

            if (isViewInitialized) {
                viewBinding().etOpacity.text = (value * 100).toInt().toString()
                viewBinding().sliderOpacityView.setProgress(value)
                changeData {
                    it.opacity = value
                }
            }
        }
    private var currentStreamline: Float = 10f
        set(value) {
            field = value

            if (isViewInitialized) {
                viewBinding().etStreamLine.text = (value).toInt().toString()
                viewBinding().sliderStreamLineView.setProgress(value / 100)
            }
        }

    private var brushes: List<DrawToolBrush> =
        DrawToolData.getAllDrawBrush(brushSelected = Brush.Pen)
        set(value) {
            field = value

            if (isViewInitialized) {
                viewBinding().setVariable(BR.brushes, value)
                viewBinding().executePendingBindings()
            }
        }

    private fun setupUI() {
        setupPreview()
        updateEraser()
        setupSliderSizeView()
        setupColorPaletteView()
        setupColorView()
        setupSliderOpacityView()
        setupSliderStreamLineView()
        setupBrushGalleryView()
    }

    private fun setupAction() {

    }

    private fun setupPreview() {
        val drawToolPen = drawToolBrush ?: return

        viewBinding().drawToolPreview.apply {
            brush = drawToolPen.brush
            mStrokeWidth = drawToolPen.sliderSize
            mColor = drawToolPen.color.toColorInt().adjustAlpha(drawToolPen.opacity)
            invalidate()
        }
    }

    private fun setupColorPaletteView() {
        viewBinding().colorPalette.apply {
            colorType = ColorType.COLOR_PALETTE
            listener = object : ColorPaletteView.OnItemClickListener {
                override fun onClick(color: ColorPickerItem) {
                    changeData {
                        it.color = color.colorString
                    }
                }
            }
            paletteColors = ColorPaletteOption.colorPaletteDefault(context)
        }
    }

    private fun setupColorView() {
        viewBinding().colorView.apply {
            colorType = ColorType.COLOR
            listener = object : ColorPaletteView.OnItemClickListener {
                override fun onClick(color: ColorPickerItem) {
                    changeData {
                        it.color = color.colorString
                    }
                }
            }
            paletteColors = ColorPaletteOption.colorDefault(context)
        }
    }

    private fun setupBrushGalleryView() {
        viewBinding().drawToolBrushGallery.apply {
            listener = object : DrawToolBrushGalleryView.OnItemClickListener {
                override fun onClick(brush: DrawToolBrush) {
                    changeData {
                        it.brush = brush.brush
                    }
                }
            }
        }
        viewBinding().setVariable(BR.brushes, brushes)
        viewBinding().executePendingBindings()
    }

    private fun changeData(block: (DrawToolBrush) -> Unit) {
        drawToolBrush = drawToolBrush?.apply(block)
    }

    private fun showPicker(anchor: View, type: ViewType) {
        val currentVal = when (type) {
            ViewType.SIZE -> currentSize
            ViewType.OPACITY -> currentOpacity
            ViewType.STREAMLINE -> currentStreamline
        }

        DrawNumberPickerView.show(
            context = context,
            anchor = anchor,
            minValue = if (type == ViewType.SIZE) 1 else 0,
            maxValue = 100,
            initialValue = currentVal.toInt(),
            listener = object : DrawNumberPickerView.OnNumberSelectedListener {
                override fun onNumberSelected(value: Int) {
                    val floatValue = value.toFloat()
                    when (type) {
                        ViewType.SIZE -> this@DrawToolConfigView.currentSize = floatValue
                        ViewType.OPACITY -> this@DrawToolConfigView.currentOpacity =
                            floatValue / 100

                        ViewType.STREAMLINE -> this@DrawToolConfigView.currentStreamline =
                            floatValue
                    }
                }
            }
        )
    }

    @SuppressLint("SetTextI18n")
    private fun setupSliderSizeView() {
        currentSize = drawToolBrush?.sliderSize ?: 5f
        viewBinding().apply {
            sliderSizeView.apply {
                setFirstGradientColor("#75E073")
                setCurrentColor("#75E073")
                setSecondGradientColor("#75E073")
                setIsOpacitySlider(false)
            }
            etSize.debounceClick {
                showPicker(it, type = ViewType.SIZE)
            }
            sliderSizeView.sliderViewListener = object : SliderView.SliderViewListener {
                override fun onProgressChanged(value: Float) {
                    this@DrawToolConfigView.currentSize = value * 100
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupSliderOpacityView() {
        currentOpacity = drawToolBrush?.opacity ?: 1f

        viewBinding().apply {
            sliderOpacityView.apply {
                setIsOpacitySlider(true)
                setSecondGradientColor("#75E073")
                setCurrentColor("#75E073")
            }
            etOpacity.debounceClick {
                showPicker(it, ViewType.OPACITY)
            }
            sliderOpacityView.sliderViewListener = object : SliderView.SliderViewListener {
                override fun onProgressChanged(value: Float) {
                    currentOpacity = value
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupSliderStreamLineView() {
        currentStreamline = 10f

        viewBinding().apply {
            sliderStreamLineView.apply {
                setFirstGradientColor("#75E073")
                setCurrentColor("#75E073")
                setSecondGradientColor("#75E073")
                setIsOpacitySlider(false)
            }
            etStreamLine.debounceClick {
                showPicker(it, ViewType.OPACITY)
            }
            sliderStreamLineView.sliderViewListener = object : SliderView.SliderViewListener {
                override fun onProgressChanged(value: Float) {
                    this@DrawToolConfigView.currentStreamline = value * 100
                }
            }
        }
    }

    private fun updateEraser() {
        val isEraser =
            drawToolBrush?.brush == Brush.SoftEraser || drawToolBrush?.brush == Brush.HardEraser
        val isBrush3D =
            drawToolBrush?.brush?.group == BrushGroup.Pen3D && drawToolBrush?.type == DrawToolPenType.CUSTOM
        val isBrushCustom = drawToolBrush?.type == DrawToolPenType.CUSTOM
        viewBinding().apply {
            tvClear.gone(!isEraser)
            llOtherBrush.gone(isEraser || isBrush3D)
            drawToolBrushGallery.gone(!isBrushCustom)
        }
    }

    companion object {
        fun show(
            context: Context,
            anchor: View,
            drawToolBrush: DrawToolBrush?,
            listener: DrawToolConfigListener,
            width: Int? = null,
            height: Int? = null,
        ) {
            val configView = DrawToolConfigView(context, null).apply {
                this.drawToolBrush = drawToolBrush
            }

            val sizePopup = when {
                context.isPhoneLandscape -> Pair(280.px, 280.px)
                context.isPhonePortrait -> Pair(320.px, 520.px)
                context.isTabletLandscape -> Pair(360.px, 620.px)
                context.isTabletPortrait -> Pair(360.px, 600.px)
                else -> {
                    Pair(280.px, 280.px)
                }
            }

            val (popupWidth, popupHeight) = sizePopup
            val popupWindow = PopupWindow(
                configView,
                popupWidth,
                height ?: popupHeight
            ).apply {
                elevation = context.resources.getDimension(R.dimen.dp_10)
                isClippingEnabled = true
                isOutsideTouchable = true
                showPopupAtLocation(
                    this, anchor = anchor, contentView = configView
                )
            }
            popupWindow.setOnDismissListener {
                val drawToolData = configView.drawToolBrush ?: return@setOnDismissListener
                listener.onChange(drawToolData)
            }
            configView.listener = object : DrawToolConfigListener {
                override fun onChange(drawToolBrush: DrawToolBrush) {
                    popupWindow.dismiss()
                }
            }
            configView.post {
                showPopupAtLocation(popupWindow, anchor = anchor, contentView = configView)
            }
        }

        private fun showPopupAtLocation(popupWindow: PopupWindow, anchor: View, contentView: View) {
            val context = anchor.context
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)

            val anchorX = location[0]
            val anchorY = location[1]
            val anchorWidth = anchor.width

            contentView.measure(
                View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED
            )

            val marginY = context.resources.getDimension(R.dimen.dp_36).toInt()
            val marginSide = context.resources.getDimension(R.dimen.dp_36).toInt()

            val screenW = context.screenSize().width
            val screenH = context.screenSize().height

            var finalX: Int
            var finalY: Int

            if (context.isTabletLandscape || context.isPhoneLandscape) {
                finalX = if (context.isTabletLandscape) {
                    screenW - anchorX - popupWindow.width * 3 + marginSide * 2
                } else {
                    screenW - popupWindow.width * 2 - marginSide * 2 - anchorX + 20.px
                }
                finalY = if (context.isTabletLandscape) {
                    screenH - popupWindow.height - 6.px
                } else {
                    (screenH - popupWindow.height) * 2
                }
            } else {
                finalX = if (context.isTabletPortrait) {
                    (screenW - popupWindow.width) / 4
                } else {
                    (screenW - popupWindow.width) / 4
                }
                finalY = if (context.isTabletPortrait) {
                    anchorY - marginY - popupWindow.height + 6.px
                } else {
                    anchorY - marginY - popupWindow.height + 10.px
                }
            }
            popupWindow.showAtLocation(
                anchor, android.view.Gravity.NO_GRAVITY, finalX, finalY
            )
        }
    }
}

