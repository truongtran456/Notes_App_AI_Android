package com.philkes.notallyx.common.extension

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.philkes.notallyx.common.model.Selectable
import com.philkes.notallyx.core.ui.adapter.TMVVMAdapter
import com.philkes.notallyx.core.ui.decorator.SpacesItemDecoration

fun RecyclerView.addDecoration(decoration: ItemDecoration) {
    if (itemDecorationCount == 0) {
        addItemDecoration(
            decoration
        )
    }
}

fun RecyclerView.doOnScrolled(scroll: (Int) -> Unit) {
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            scroll(computeVerticalScrollOffset())
        }
    })
}

fun RecyclerView.scrollToBottom() {
    val layoutManager = layoutManager as? LinearLayoutManager ?: return
    val adapter = adapter ?: return
    val lastItemPosition = adapter.itemCount - 1
    layoutManager.scrollToPositionWithOffset(lastItemPosition, 0)
    post { // then scroll to specific offset
        val target = layoutManager.findViewByPosition(lastItemPosition)
        if (target != null) {
            val offset: Int = measuredHeight - target.measuredHeight
            layoutManager.scrollToPositionWithOffset(lastItemPosition, offset)
        }
    }
}

fun RecyclerView.addSpaceDecoration(space: Int, includeEdge: Boolean = true) {
    addDecoration(SpacesItemDecoration(space, includeEdge))
}

fun <T: Selectable> TMVVMAdapter<T>.reloadChangedItems(predicate: (T) -> Boolean): List<Int> {
    val previousIndex = list.indexOfFirst {  it.isSelected == true }
    val currentIndex = list.indexOfFirst { predicate(it) }

    if (previousIndex != currentIndex) {
        for (item in list) {
            item.isSelected = predicate(item)
        }
        notifyItemChanged(previousIndex)
        notifyItemChanged(currentIndex)

        return arrayListOf(previousIndex, currentIndex)
    }

    return arrayListOf()
}

fun <T: Selectable> RecyclerView.reloadChangedItems(predicate: (T) -> Boolean) {
    (this.adapter as? TMVVMAdapter<T>)?.apply {
        reloadChangedItems(predicate)
    }
}

