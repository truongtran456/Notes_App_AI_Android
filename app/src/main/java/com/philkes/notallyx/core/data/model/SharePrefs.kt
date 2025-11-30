package com.philkes.notallyx.core.data.model

interface SharePrefs {
    var currentCodeLang: String
    var currentCountryCode: String
    var installTime: Long
    var isFirstOpen: Boolean
    var isPurchased: Boolean
    var openTimes: Int
    var deviceId: String?
}

