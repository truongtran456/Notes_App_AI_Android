package com.starnest.common.ui.handler

import android.graphics.RectF

interface UserActionHandler<T> {
    fun handleAction(action: T)
}