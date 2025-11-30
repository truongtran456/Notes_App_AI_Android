package com.starnest.common.navigation

import android.content.Context
import java.util.UUID

interface DrawNavigator {
    fun openCreateNewDraw(context: Context)

    fun openNewDraw(context: Context, drawId: UUID?)

    fun openCusTomDialog(context: Context)

    fun openLessonDrawScreen(context: Context, drawLessonStepId: UUID, nextLessonStepId: UUID? = null)
}