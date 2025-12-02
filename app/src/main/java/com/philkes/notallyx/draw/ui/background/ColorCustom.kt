package com.philkes.notallyx.draw.ui.background

import android.os.Parcelable
import com.philkes.notallyx.common.ui.view.colorview.ColorPickerItem
import com.philkes.notallyx.common.model.Selectable
import kotlinx.parcelize.Parcelize

interface ColorCustomItem : ColorPickerItem

@Parcelize
data class ColorCustom(
    val id: String,
    override var colorString: String,
    override val isMore: Boolean = false,
    override var isSelected: Boolean = false,
    override val isAddColor: Boolean = false,
    override val isNone: Boolean = false,
) : ColorCustomItem, Parcelable, Selectable


