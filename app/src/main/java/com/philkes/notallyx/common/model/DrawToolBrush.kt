package com.philkes.notallyx.common.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

enum class DrawToolPenType(val value: String) {
    CUSTOM("custom"),
    DEFAULT_PEN("default"),
    ERASER("eraser")
}

interface Selectable {
    var isSelected: Boolean
}

@Parcelize
data class DrawToolBrush(
    var id: UUID = UUID.randomUUID(),
    var brush: Brush,
    var opacity: Float = 1f,
    var sliderSize: Float = 5f,
    var color: String = "#000000",
    val isAdd: Boolean = false,
    var order: Int = 0,
    val isShowDelete: Boolean = false,
    var type: DrawToolPenType = DrawToolPenType.CUSTOM,
    override var isSelected: Boolean = false
) : Selectable, Parcelable {

}
