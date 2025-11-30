package com.starnest.common.extension

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.scrollToBottomRespectPadding() {
    val layoutManager = layoutManager as? LinearLayoutManager ?: return
    val adapter = adapter ?: return
    val lastItemPosition = adapter.itemCount - 1
    if (lastItemPosition < 0) return

    layoutManager.scrollToPositionWithOffset(lastItemPosition, 0)

    post {
        val lastView = layoutManager.findViewByPosition(lastItemPosition) ?: return@post

        val bottomPadding = paddingBottom
        val offset = measuredHeight - lastView.measuredHeight - bottomPadding

        layoutManager.scrollToPositionWithOffset(lastItemPosition, offset)
    }
}