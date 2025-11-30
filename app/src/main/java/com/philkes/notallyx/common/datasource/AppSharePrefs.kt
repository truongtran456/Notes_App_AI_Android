package com.philkes.notallyx.common.datasource

import com.philkes.notallyx.common.model.DrawToolBrush
import com.philkes.notallyx.common.model.TextFormat
import com.philkes.notallyx.core.data.model.SharePrefs

interface AppSharePrefs : SharePrefs {
    var hadASuccessfulKeyboardInstallation: Boolean
    var isChatSuggestion: Boolean
    var isEnableHomeSendAnimation: Boolean

    var passcode: String?

    var isAppLock: Boolean

    var isFaceID: Boolean

    var isDailyNotificationSetup: Boolean

    var drawToolBrushes: ArrayList<DrawToolBrush>
    var drawToolSelectedColors: ArrayList<String>
    var textFormat: TextFormat

}