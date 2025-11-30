package com.philkes.notallyx.common.datasource

import android.content.Context

val Context.usageConfig: UsageConfig get() = UsageConfigImpl.newInstance(applicationContext)

class UsageConfigImpl(val context: Context) : UsageConfig {

    private val sharedPrefs = context.getSharedPreferences(
        Keys.SHARED_PREFS_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private var shared: UsageConfig? = null

        fun newInstance(context: Context): UsageConfig {
            synchronized(this) {
                if (shared == null) {

                    shared = UsageConfigImpl(context)
                }
                return shared!!
            }
        }

        object Keys {
            const val SHARED_PREFS_NAME: String = "UsageConfig"
            const val IS_USER_RATED = "IS_USER_RATED"
            const val TOTAL_CHAT_MESSAGE = "TOTAL_CHAT_MESSAGE"
            const val APP_OPEN_SESSION = "APP_OPEN_SESSION"
            const val REQUEST_TIMES = "REQUEST_TIMES"
            const val CLICK_PREMIUM_IN_NAV_TIMES = "CLICK_PREMIUM_IN_NAV_TIMES"
            const val TIME_OPEN_APP = "TIME_OPEN_APP"
            const val RESPONSE_TIMES = "RESPONSE_TIMES"
        }

    }

    override var totalChatMessage: Int
        get() = sharedPrefs.getInt(Keys.TOTAL_CHAT_MESSAGE, 0)
        set(value) {
            sharedPrefs.edit().putInt(Keys.TOTAL_CHAT_MESSAGE, value).apply()
        }
    override var responseTimes: Int
        get() = sharedPrefs.getInt(Keys.RESPONSE_TIMES, 0)
        set(value) {
            if (value > 1_000_000) {
                return
            }
            sharedPrefs.edit().putInt(Keys.RESPONSE_TIMES, value).apply()
        }

    override var requestTimes: Int
        get() = sharedPrefs.getInt(Keys.REQUEST_TIMES, 0)
        set(value) {
            if (value > 1_000_000) {
                return
            }
            sharedPrefs.edit().putInt(Keys.REQUEST_TIMES, value).apply()
        }
    override var isUserRated: Boolean
        get() = sharedPrefs.getBoolean(Keys.IS_USER_RATED, false)
        set(value) {
            sharedPrefs.edit().putBoolean(Keys.IS_USER_RATED, value).apply()
        }

    override var appOpenSession: Int
        get() = sharedPrefs.getInt(Keys.APP_OPEN_SESSION, 0)
        set(value) {
            sharedPrefs.edit().putInt(Keys.APP_OPEN_SESSION, value).apply()
        }
    override var clickPremiumInNavTimes: Int
        get() = sharedPrefs.getInt(Keys.CLICK_PREMIUM_IN_NAV_TIMES, 0)
        set(value) {
            if (value > 1_000_000) {
                return
            }
            sharedPrefs.edit().putInt(Keys.CLICK_PREMIUM_IN_NAV_TIMES, value).apply()
        }

    override var timeOpenApp: Int
        get() = sharedPrefs.getInt(Keys.TIME_OPEN_APP, 0)
        set(value) {
            sharedPrefs.edit().putInt(Keys.TIME_OPEN_APP, value).apply()
        }


}