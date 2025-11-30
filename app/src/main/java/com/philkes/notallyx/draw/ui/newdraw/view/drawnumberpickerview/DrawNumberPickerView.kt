package com.philkes.notallyx.draw.ui.newdraw.view.drawnumberpickerview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import com.philkes.notallyx.R
import com.philkes.notallyx.common.extension.px
import com.philkes.notallyx.core.ui.widget.AbstractView
import com.philkes.notallyx.databinding.ItemDrawNumberPickerViewBinding

@SuppressLint("ViewConstructor")
class DrawNumberPickerView(
    context: Context,
    attrs: AttributeSet?,
) : AbstractView(context, attrs) {

    override fun layoutId(): Int = R.layout.item_draw_number_picker_view

    interface OnNumberSelectedListener {
        fun onNumberSelected(value: Int)
    }

    var listener: OnNumberSelectedListener? = null
    var minValue: Int = 0
    var maxValue: Int = 100
    var currentValue: Int = 5

    override fun viewInitialized() {
        setupUI()
    }

    override fun viewBinding(): ItemDrawNumberPickerViewBinding =
        binding as ItemDrawNumberPickerViewBinding

    private fun setupUI() {
        post { setupNumberPicker() }
    }

    private fun setupNumberPicker() {
        viewBinding().numberPicker.apply {
            isAccessibilityDescriptionEnabled = false
            setOnValueChangedListener { _, oldVal, newVal ->
                if (oldVal != newVal) {
                    listener?.onNumberSelected(newVal)
                }
            }
            maxValue = this@DrawNumberPickerView.maxValue
            minValue = this@DrawNumberPickerView.minValue
            value = this@DrawNumberPickerView.currentValue
        }
    }

    companion object {
        fun show(
            context: Context,
            anchor: View,
            minValue: Int,
            maxValue: Int,
            initialValue: Int,
            listener: OnNumberSelectedListener? = null,
        ) {
            val configView =
                DrawNumberPickerView(
                    context = context,
                    attrs = null,
                ).apply {
                    this.minValue = minValue
                    this.maxValue = maxValue
                    this.currentValue = initialValue
                    this.listener = listener
                }

            val popupWindow =
                PopupWindow(
                    configView,
                    LayoutParams.WRAP_CONTENT,
                    200.px,
                    true,
                ).apply {
                    setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.bg_corner_16))
                    elevation = context.resources.getDimension(R.dimen.dp_10)
                    isClippingEnabled = true
                    isOutsideTouchable = true
                }
            popupWindow.showAsDropDown(anchor)
        }
    }
}

