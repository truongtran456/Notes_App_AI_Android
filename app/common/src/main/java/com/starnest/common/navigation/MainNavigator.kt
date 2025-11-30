package com.starnest.common.navigation

import android.content.Context

interface MainNavigator {
    fun openMain(context: Context, isFirstLaunch: Boolean)
}