package com.philkes.notallyx.draw.ui.newdraw.view.drawtoolsizepenview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.PopupWindow
import com.philkes.notallyx.R
import com.philkes.notallyx.common.extension.debounceClick
import com.philkes.notallyx.common.extension.isPhoneLandscape
import com.philkes.notallyx.common.extension.isPhonePortrait
import com.philkes.notallyx.common.extension.isTabletLandscape
import com.philkes.notallyx.common.extension.isTabletPortrait
import com.philkes.notallyx.common.extension.px
import com.philkes.notallyx.common.extension.screenSize
import com.philkes.notallyx.common.model.DrawToolBrush
import com.philkes.notallyx.core.ui.widget.AbstractView
import com.philkes.notallyx.databinding.ItemDrawToolSizePenBinding
import com.philkes.notallyx.draw.ui.newdraw.view.drawnumberpickerview.DrawNumberPickerView
import com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig.ViewType

enum class DrawToolValueType {
    SIZE, OPACITY, STREAMLINE
}

class DrawToolSizePenView(
    context: Context,
    attrs: AttributeSet?,
) : AbstractView(context, attrs) {

    interface OnValueChangeListener {
        fun onValueChanged(type: DrawToolValueType, value: Float)
    }

    var listener: OnValueChangeListener? = null

    var type: DrawToolValueType = DrawToolValueType.SIZE
    private var currentValue: Float = 0f
        set(value) {
            field = value
            if (!isViewInitialized) return

            viewBinding().tvSize.text = when (type) {
                DrawToolValueType.SIZE -> "${value.toInt()} px"
                DrawToolValueType.OPACITY -> "${(value * 100).toInt()} %"
                DrawToolValueType.STREAMLINE -> "${value.toInt()} %"
            }
            viewBinding().sliderSizeView.setProgress(
                when (type) {
                    DrawToolValueType.OPACITY -> value
                    else -> value / 100
                }
            )
        }

    override fun layoutId(): Int = R.layout.item_draw_tool_size_pen

    override fun viewBinding(): ItemDrawToolSizePenBinding {
        return binding as ItemDrawToolSizePenBinding
    }

    var drawToolBrush: DrawToolBrush? = null
        set(value) {
            field = value

            if (isViewInitialized) {
                currentValue = when (type) {
                    DrawToolValueType.SIZE -> value?.sliderSize ?: 5f
                    DrawToolValueType.OPACITY -> value?.opacity ?: 1f
                    else -> {
                        0f
                    }
                }
            }
        }

    override fun viewInitialized() {
        setupUI()
    }

    private fun setupUI() {
        setupSliderSizeView()
    }

    @SuppressLint("SetTextI18n")
    private fun setupSliderSizeView() {
        viewBinding().apply {
            sliderSizeView.apply {
                setFirstGradientColor("#75E073")
                setCurrentColor("#75E073")
                setSecondGradientColor("#75E073")
                setIsOpacitySlider(type == DrawToolValueType.OPACITY)
            }
            tvSize.debounceClick {
                showSizePicker(it, type)
            }
            sliderSizeView.sliderViewListener = object : com.philkes.notallyx.common.ui.view.sliderview.SliderView.SliderViewListener {
                override fun onProgressChanged(value: Float) {
                    currentValue = when (type) {
                        DrawToolValueType.SIZE -> value * 100
                        DrawToolValueType.OPACITY -> value
                        DrawToolValueType.STREAMLINE -> value * 100
                    }
                    listener?.onValueChanged(type, currentValue)
                    changeData {
                        when (type) {
                            DrawToolValueType.SIZE -> it.sliderSize = value * 100
                            DrawToolValueType.OPACITY -> it.opacity = value
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun showSizePicker(anchor: View, type: DrawToolValueType) {
        DrawNumberPickerView.show(
            context = context,
            anchor = anchor,
            minValue = if (type == DrawToolValueType.SIZE) 1 else 0,
            maxValue = 100,
            initialValue = currentValue.toInt(),
            listener = object : DrawNumberPickerView.OnNumberSelectedListener {
                override fun onNumberSelected(value: Int) {
                    currentValue =
                        if (type == DrawToolValueType.OPACITY) value / 100f else value.toFloat()
                }
            }
        )
    }

    private fun changeData(block: (DrawToolBrush) -> Unit) {
        drawToolBrush = drawToolBrush?.apply(block)
    }

    companion object {
        fun show(
            context: Context,
            anchor: View,
            type: DrawToolValueType,
            initialValue: Float? = null,
            width: Int? = null,
            listener: OnValueChangeListener,
            brush: DrawToolBrush,
            onChange: (DrawToolBrush) -> Unit
        ) {
            val configView = DrawToolSizePenView(context, null).apply {
                drawToolBrush = brush
                this.type = type
                this.currentValue = initialValue ?: when (type) {
                    DrawToolValueType.SIZE -> 5f
                    DrawToolValueType.OPACITY -> 1f
                    DrawToolValueType.STREAMLINE -> 10f
                }
                this.listener = listener
            }
            val sizePopup = when {
                context.isPhoneLandscape -> Pair(260.px, 260.px)
                context.isPhonePortrait -> Pair(320.px, LayoutParams.WRAP_CONTENT)
                context.isTabletLandscape -> Pair(360.px, 320.px)
                context.isTabletPortrait -> Pair(400.px, LayoutParams.WRAP_CONTENT)
                else -> Pair(280.px, 280.px)
            }
            val (popupWidth, popupHeight) = sizePopup
            val popupWindow = PopupWindow(
                configView,
                popupWidth,
                popupHeight,
                true
            ).apply {
                elevation = context.resources.getDimension(R.dimen.dp_10)
                isClippingEnabled = true
                isOutsideTouchable = true
                showPopupAtLocation(this, anchor = anchor, contentView = configView)
            }
            popupWindow.setOnDismissListener {
                configView.drawToolBrush?.let {
                    onChange(it)
                }
            }
            configView.post {
                showPopupAtLocation(popupWindow, anchor = anchor, contentView = configView)
            }
        }

        private fun showPopupAtLocation(
            popupWindow: PopupWindow,
            anchor: View,
            contentView: View
        ) {
            val context = anchor.context
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)

            val anchorX = location[0]
            val anchorY = location[1]
            val anchorWidth = anchor.width

            val marginY = context.resources.getDimension(R.dimen.dp_20).toInt()
            val marginX = context.resources.getDimension(R.dimen.dp_20).toInt()

            val screenW = context.screenSize().width
            val screenH = context.screenSize().height

            var finalX: Int
            var finalY: Int

            if (context.isTabletLandscape || context.isPhoneLandscape) {
                finalX = if (context.isTabletLandscape) {
                    popupWindow.width - anchorX * 3 + anchorWidth
                } else {
                    popupWindow.width - anchorX * 3 + anchorWidth - marginX
                }
                finalY = if (context.isTabletLandscape) {
                    (screenH - popupWindow.height) / 2 + marginY
                } else {
                    screenH - popupWindow.height + marginY * 2 - 16.px
                }
                contentView.rotation = 90f
            } else {
                finalX = (screenW - popupWindow.width) / 2
                finalY = anchorY - marginY * 3 - popupWindow.height
                contentView.rotation = 0f
            }
            popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, finalX, finalY)
        }
    }
}

