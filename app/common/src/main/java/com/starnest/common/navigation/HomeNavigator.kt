package com.starnest.common.navigation

import android.content.Context
import java.util.UUID

interface HomeNavigator {
    fun openDetail(context: Context, name: String, categoryId: String)
}