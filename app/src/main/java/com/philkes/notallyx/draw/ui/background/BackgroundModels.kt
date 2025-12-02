package com.philkes.notallyx.draw.ui.background

import androidx.annotation.ColorInt

enum class BackgroundCategoryType {
    NATURE,
    PASTEL,
    TEXTURE,
    CUSTOM,
}

data class BackgroundItem(
    val id: String,
    @ColorInt val colorInt: Int,
    val category: BackgroundCategoryType,
    val isCustomAdd: Boolean = false,
    var isSelected: Boolean = false,
)

data class BackgroundSection(
    val type: BackgroundCategoryType,
    val title: String,
    val items: MutableList<BackgroundItem>,
)


