package com.starnest.common.ui.handler

interface ResultLauncherHandler<T>: UserActionHandler<T> {
    fun registerResultLaunchers()

    fun unregisterResultLaunchers()
}