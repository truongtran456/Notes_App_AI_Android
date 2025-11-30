package com.philkes.notallyx.common.datasource

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.philkes.notallyx.common.model.DrawToolBrush
import com.philkes.notallyx.common.model.TextFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSharePrefsImpl @Inject constructor(
    private val sharedPrefs: SharedPreferences,
    private val gson: Gson,
) : AppSharePrefs {

    object Keys {
        const val SHARED_PREFS_NAME = "LearnDraw"
        const val CURRENT_LANGUAGE = "CURRENT_LANGUAGE"
        const val INSTALL_TIME = "INSTALL_TIME"
        const val CURRENT_COUNTRY_CODE = "CURRENT_COUNTRY_CODE"
        const val IS_FIRST_TIME = "IS_FIRST_TIME"
        const val IS_PURCHASED = "IS_PURCHASED"
        const val OPEN_TIMES = "OPEN_TIMES"
        const val DEVICE_ID = "DEVICE_ID"
        const val HAD_A_SUCCESSFUL_KEYBOARD_INSTALLATION = "HAD_A_SUCCESSFUL_KEYBOARD_INSTALLATION"
        const val IS_CHAT_SUGGESTION = "IS_CHAT_SUGGESTION"
        const val IS_ENABLE_HOME_SEND_ANIMATION = "IS_ENABLE_HOME_SEND_ANIMATION"

        const val PASSCODE = "PASSCODE"

        const val IS_FACE_ID = "IS_FACE_ID"

        const val IS_APP_LOCK = "IS_APP_LOCK"
        const val DRAW_TOOL_PEN = "DRAW_TOOL_PEN"

        const val IS_DAILY_NOTIFICATION_SETUP = "IS_DAILY_NOTIFICATION_SETUP"
        const val DRAW_TOOL_SELECTED_COLOR = "DRAW_TOOL_SELECTED_COLOR"
        const val TEXT_FORMAT = "TEXT_FORMAT"
    }

    override var currentCodeLang: String
        get() {
            val format = sharedPrefs.getString(Keys.CURRENT_LANGUAGE, null)
            return if (format.isNullOrEmpty()) {
                Locale.getDefault().language
            } else {
                format
            }
        }
        set(value) {
            sharedPrefs.edit().putString(Keys.CURRENT_LANGUAGE, value).apply()
        }

    override var currentCountryCode: String
        get() = sharedPrefs.getString(Keys.CURRENT_COUNTRY_CODE, "") ?: ""
        set(value) {
            sharedPrefs.edit().putString(Keys.CURRENT_COUNTRY_CODE, value).apply()
        }

    override var installTime: Long
        get() = sharedPrefs.getLong(Keys.INSTALL_TIME, 0L)
        set(value) {
            sharedPrefs.edit().putLong(Keys.INSTALL_TIME, value).apply()
        }

    override var isFirstOpen: Boolean
        get() = sharedPrefs.getBoolean(Keys.IS_FIRST_TIME, true)
        set(value) {
            sharedPrefs.edit().putBoolean(Keys.IS_FIRST_TIME, value).apply()
        }

    override var isPurchased: Boolean
        get() = sharedPrefs.getBoolean(Keys.IS_PURCHASED, false)
        set(value) {
            sharedPrefs.edit().putBoolean(Keys.IS_PURCHASED, value).apply()
        }

    override var openTimes: Int
        get() = sharedPrefs.getInt(Keys.OPEN_TIMES, 0)
        set(value) {
            sharedPrefs.edit().putInt(Keys.OPEN_TIMES, value).apply()
        }
    override var deviceId: String?
        get() = sharedPrefs.getString(Keys.DEVICE_ID, null)
        set(value) {
            sharedPrefs.edit().putString(Keys.DEVICE_ID, value).apply()
        }
    override var hadASuccessfulKeyboardInstallation: Boolean
        get() = sharedPrefs.getBoolean(Keys.HAD_A_SUCCESSFUL_KEYBOARD_INSTALLATION, false)
        set(value) {
            sharedPrefs.edit().putBoolean(Keys.HAD_A_SUCCESSFUL_KEYBOARD_INSTALLATION, value).apply()
        }
    override var isChatSuggestion: Boolean
        get() = sharedPrefs.getBoolean(Keys.IS_CHAT_SUGGESTION, true)
        set(value) {
            sharedPrefs.edit().putBoolean(Keys.IS_CHAT_SUGGESTION, value).apply()
        }
    override var isEnableHomeSendAnimation: Boolean
        get() = sharedPrefs.getBoolean(Keys.IS_ENABLE_HOME_SEND_ANIMATION, true)
        set(value) {
            sharedPrefs.edit().putBoolean(Keys.IS_ENABLE_HOME_SEND_ANIMATION, value).apply()
        }

    override var passcode: String?
        get() = sharedPrefs.getString(Keys.PASSCODE, null)
        set(value) {
            sharedPrefs.edit().putString(Keys.PASSCODE, value).apply()
        }

    override var isAppLock: Boolean
        get() = sharedPrefs.getBoolean(Keys.IS_APP_LOCK, false)
        set(value) {
            sharedPrefs.edit().putBoolean(Keys.IS_APP_LOCK, value).apply()
        }

    override var isFaceID: Boolean
        get() = sharedPrefs.getBoolean(Keys.IS_FACE_ID, false)
        set(value) {
            sharedPrefs.edit().putBoolean(Keys.IS_FACE_ID, value).apply()
        }

    override var isDailyNotificationSetup: Boolean
        get() = sharedPrefs.getBoolean(Keys.IS_DAILY_NOTIFICATION_SETUP, false)
        set(value) {
            sharedPrefs.edit().putBoolean(Keys.IS_DAILY_NOTIFICATION_SETUP, value).apply()
        }

    override var drawToolBrushes: ArrayList<DrawToolBrush>
        get() {
            val listType = object : TypeToken<ArrayList<DrawToolBrush?>?>() {}.type
            val value = sharedPrefs.getString(Keys.DRAW_TOOL_PEN, "")
            if (value.isNullOrEmpty()) {
                return ArrayList()
            }
            return gson.fromJson(value, listType)
        }
        set(value) {
            sharedPrefs.edit().putString(Keys.DRAW_TOOL_PEN, gson.toJson(value)).apply()
        }

    override var drawToolSelectedColors: ArrayList<String>
        get() {
            val listType = object : TypeToken<ArrayList<String?>?>() {}.type
            val value = sharedPrefs.getString(Keys.DRAW_TOOL_SELECTED_COLOR, "")
            if (value.isNullOrEmpty()) {
                return ArrayList()
            }
            return gson.fromJson(value, listType)
        }
        set(value) {
            sharedPrefs.edit().putString(Keys.DRAW_TOOL_SELECTED_COLOR, gson.toJson(value)).apply()
        }
    override var textFormat: TextFormat
        get() {
            val value = sharedPrefs.getString(Keys.TEXT_FORMAT, null)
            return value?.let { gson.fromJson(it, TextFormat::class.java) } ?: TextFormat()
        }
        set(value) {
            sharedPrefs.edit().putString(Keys.TEXT_FORMAT, gson.toJson(value)).apply()
        }
}