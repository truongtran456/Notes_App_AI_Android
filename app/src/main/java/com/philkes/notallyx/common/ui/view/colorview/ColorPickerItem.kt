package com.philkes.notallyx.common.ui.view.colorview

import com.philkes.notallyx.common.extension.color
import com.philkes.notallyx.common.model.Selectable

interface ColorPickerItem : Selectable {
    var colorString: String
    val isNone: Boolean
    val isMore: Boolean
    val isAddColor: Boolean

    override var isSelected: Boolean

    val color: Int
        get() = colorString.color
}
