package com.starnest.common.ui.view.menuview

import kotlinx.serialization.Serializable

enum class MenuOptionType {
    CHAT_SUGGESTION, NEW_CHAT, SHARE, HISTORY, DELETE, CLEAR_CHAT, RENAME, EDIT, DUPLICATE, EXPORT, MOVE, RESTORE, MERGE, TRANSFORM, LOCK
}


@Serializable
data class MenuOption(
    val type: MenuOptionType,
    val iconResId: Int,
    val nameResId: Int,
    val toggleEnabled: Boolean = false,
    val isChecked: Boolean = false,
    val tintColor: Int? = null,
) {
}