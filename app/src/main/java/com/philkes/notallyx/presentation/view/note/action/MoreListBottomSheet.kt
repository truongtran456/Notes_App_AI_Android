package com.philkes.notallyx.presentation.view.note.action

import androidx.annotation.ColorInt
import com.philkes.notallyx.R

/** BottomSheet inside list-note for all common note actions and list-item actions. */
class MoreListBottomSheet(
    callbacks: MoreListActions,
    additionalActions: Collection<Action> = listOf(),
    @ColorInt color: Int?,
) : ActionBottomSheet(createActions(callbacks, additionalActions), color) {

    companion object {
        const val TAG = "MoreListBottomSheet"

        private fun createActions(callbacks: MoreListActions, actions: Collection<Action>) =
            MoreNoteBottomSheet.createActions(callbacks, actions)
    }
}

interface MoreListActions : MoreActions {
    fun deleteChecked()

    fun checkAll()

    fun uncheckAll()
}
