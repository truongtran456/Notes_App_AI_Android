package com.philkes.notallyx.core.data.model

data class RecyclerItem<T>(val type: ViewType, val data: T?) {
    enum class ViewType(val value: Int) {
        HEADER(0), NORMAL(1)
    }
}

