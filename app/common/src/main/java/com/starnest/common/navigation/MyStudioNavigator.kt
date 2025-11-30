package com.starnest.common.navigation

import android.content.Context
import androidx.fragment.app.FragmentManager

interface MyStudioNavigator {
    fun openCategoryStudio(context: Context)

    fun openSearchStudio(context: Context)
}