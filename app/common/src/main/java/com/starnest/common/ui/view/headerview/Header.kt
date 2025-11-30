package com.starnest.common.ui.view.headerview

data class Header(
    val iconResId: Int,
    val title: String,
    val titleColor: Int = 0,
    val bgResId: Int = 0,
    var isSeeAllEnabled: Boolean = true
) {
}