package com.starnest.common.util

import android.content.Context
import com.starnest.core.data.model.DatePattern
import com.starnest.core.extension.format
import com.starnest.core.extension.isToday
import com.starnest.core.extension.isYesterday
import com.starnest.resources.R
import java.util.Date

object DateTimeUtil {
    fun getDisplayTimeInHistory(context: Context, date: Date): String {
        if (date.isToday()) {
            return context.getString(R.string.today)
        }
        if (date.isYesterday()) {
            return context.getString(R.string.yesterday)
        }
        return date.format(DatePattern.DD_MM_YYYY)
    }
}