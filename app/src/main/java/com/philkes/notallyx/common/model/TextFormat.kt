package com.philkes.notallyx.common.model

data class TextFormat(
    val fontSize: Int = 16,
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 1f,
    val color: String = "#000000",
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
)

