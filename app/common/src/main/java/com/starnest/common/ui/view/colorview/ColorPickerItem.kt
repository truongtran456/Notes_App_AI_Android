package com.starnest.common.ui.view.colorview

import com.starnest.core.data.model.Selectable
import com.starnest.core.extension.color

interface ColorPickerItem : Selectable {
    var colorString: String
    val isNone: Boolean
    val isMore: Boolean
    val isAddColor: Boolean

    override var isSelected: Boolean

    val color: Int
        get() = colorString.color
}
