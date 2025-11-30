package com.starnest.common.navigation

import android.content.Context
import java.util.UUID

interface LessonNavigator {
    fun openWatchTutorialScreen(context: Context, url: String)

    fun openLessonStepScreen(context: Context, drawLessonActivityId: UUID)

    fun openLessonResultScreen(
        context: Context,
        drawLessonStepId: UUID,
        nextLessonStepId: UUID? = null
    )
}