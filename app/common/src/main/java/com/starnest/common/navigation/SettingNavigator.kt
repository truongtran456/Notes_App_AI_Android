package com.starnest.common.navigation

import android.content.Context
import com.starnest.common.ui.passcode.ModePin

interface SettingNavigator {
    fun openSetting(context: Context)

    fun openTrashSetting(context: Context)

    fun openNotification(context: Context)

    fun openInputPassCode(context: Context, mode: ModePin)

    fun openPassCode(context: Context)

    fun openLanguage(context: Context)


}