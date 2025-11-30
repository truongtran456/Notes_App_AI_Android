package com.starnest.common.ui.databinding

import android.widget.TextView
import androidx.annotation.ArrayRes
import androidx.databinding.BindingAdapter
import com.starnest.common.extension.setGradientTextColor

object TextViewBindingAdapter {
    @BindingAdapter(value = ["gradientColors"])
    @JvmStatic
    fun setGradientColors(view: TextView, @ArrayRes colorArrayRes: Int) {
        val res = view.resources
        val typedArray = res.obtainTypedArray(colorArrayRes)

        val colors = arrayListOf<Int>()
        for (i in 0 until typedArray.length()) {
            colors.add(typedArray.getColor(i, 0))
        }
        typedArray.recycle()

        view.setGradientTextColor(colors = colors)
    }
}