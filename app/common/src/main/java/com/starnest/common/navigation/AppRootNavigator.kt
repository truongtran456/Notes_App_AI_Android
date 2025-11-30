package com.starnest.common.navigation

import androidx.fragment.app.Fragment

interface AppRootNavigator {
}

val Fragment.rootNavigator: AppRootNavigator?
    get() = activity as? AppRootNavigator
