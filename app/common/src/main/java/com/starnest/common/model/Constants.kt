package com.starnest.common.model

import com.onegravity.rteditor.fonts.RTTypeface
import java.util.concurrent.CopyOnWriteArrayList

object Constants {
    object Intents {
        const val MEDIA_PICKER_OPTION = "MEDIA_PICKER_OPTION"
        const val IMAGE_URI = "IMAGE_URI"
        const val CROP_DATA = "CROP_DATA"
        const val IS_RETAKE = "IS_RETAKE"
        const val IS_CAMERA = "IS_CAMERA"
        const val MESSAGE = "MESSAGE"
        const val MODE_PIN_CODE = "MODE_PIN_CODE"

    }

    var fonts = CopyOnWriteArrayList<RTTypeface>()
}