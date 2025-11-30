package com.starnest.common.ui.databinding

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.BindingAdapter

object ViewBindingAdapter {
    @BindingAdapter(value = ["app:layout_constraintHorizontal_bias"])
    @JvmStatic
    fun setHorizontalBias(view: View, bias: Float) {
        val params = view.layoutParams
        if (params is ConstraintLayout.LayoutParams) {
            params.horizontalBias = bias
            view.layoutParams = params
        }
    }
}