package com.philkes.notallyx.data.preferences

import android.content.Context
import java.util.UUID

object AIUserPreferences {
    private const val PREFS_NAME = "ai_settings"
    private const val KEY_USER_ID = "user_id"

    fun getOrCreateUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_USER_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_USER_ID, generated).apply()
        return generated
    }

    fun setUserId(context: Context, userId: String) {
        val sanitized = userId.trim().ifEmpty { UUID.randomUUID().toString() }
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ID, sanitized)
            .apply()
    }
}

fun Context.getAiUserId(): String = AIUserPreferences.getOrCreateUserId(this)
