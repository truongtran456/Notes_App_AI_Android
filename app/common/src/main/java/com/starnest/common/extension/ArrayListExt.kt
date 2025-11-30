package com.starnest.common.extension

import com.starnest.core.data.model.Selectable
import kotlin.collections.withIndex

fun <T : Selectable> ArrayList<T>.markSelectItem(predicate: (T, Int) -> Boolean) {
    for ((index, item) in this.withIndex()) {
        item.isSelected = predicate(item, index)
    }
}
