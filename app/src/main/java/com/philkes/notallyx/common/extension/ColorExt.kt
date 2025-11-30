package com.philkes.notallyx.common.extension

import android.graphics.Color
import kotlin.math.round

fun Int.adjustAlpha(factor: Float): Int  {
    val alpha = round(Color.alpha(this) * factor).toInt()
    val red: Int = Color.red(this)
    val green: Int = Color.green(this)
    val blue: Int = Color.blue(this)
    return Color.argb(alpha, red, green, blue)
}


fun Int.rawColor(): String {
    return "#%x".format(this)
}

fun Int.colorString(): String {
    if (this == 0) {
        return ""
    }
    return "#%x".format(this)
}