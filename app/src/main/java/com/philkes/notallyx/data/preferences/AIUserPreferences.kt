package com.philkes.notallyx.data.preferences

import android.content.Context
import java.util.UUID

object AIUserPreferences {
    private const val PREFS_NAME = "ai_settings"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_BACKEND_NOTE_ID_PREFIX = "backend_note_id_"
    private const val KEY_CONTENT_HASH_PREFIX = "note_content_hash_"

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

    /**
     * L?y backend_note_id t??ng ?ng v?i local note_id N?u ch?a có thì tr? v? null (s? dùng local
     * note_id)
     */
    fun getBackendNoteId(context: Context, localNoteId: Long): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("$KEY_BACKEND_NOTE_ID_PREFIX$localNoteId", null)
    }

    /** L?u backend_note_id t??ng ?ng v?i local note_id */
    fun setBackendNoteId(context: Context, localNoteId: Long, backendNoteId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$KEY_BACKEND_NOTE_ID_PREFIX$localNoteId", backendNoteId).apply()
    }

    /** Xóa backend_note_id (khi note b? xóa) */
    fun removeBackendNoteId(context: Context, localNoteId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("$KEY_BACKEND_NOTE_ID_PREFIX$localNoteId").apply()
    }

    private fun buildContentHashKey(localNoteId: Long, mode: String): String {
        return "$KEY_CONTENT_HASH_PREFIX${localNoteId}_$mode"
    }

    fun getNoteContentHash(context: Context, localNoteId: Long, mode: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(buildContentHashKey(localNoteId, mode), null)
    }

    fun setNoteContentHash(context: Context, localNoteId: Long, mode: String, hash: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(buildContentHashKey(localNoteId, mode), hash).apply()
    }

    fun clearNoteContentHash(context: Context, localNoteId: Long, mode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(buildContentHashKey(localNoteId, mode)).apply()
    }

    private const val KEY_TRANSLATE_LANGUAGE = "translate_language"
    private const val DEFAULT_TRANSLATE_LANGUAGE = "Vietnamese"

    /** L?y ngôn ng? translate ?ã ch?n, m?c ??nh là Vietnamese */
    fun getTranslateLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TRANSLATE_LANGUAGE, DEFAULT_TRANSLATE_LANGUAGE)
            ?: DEFAULT_TRANSLATE_LANGUAGE
    }

    /** L?u ngôn ng? translate ?ã ch?n */
    fun setTranslateLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TRANSLATE_LANGUAGE, language).apply()
    }
}

fun Context.getAiUserId(): String = AIUserPreferences.getOrCreateUserId(this)

/** L?y backend_note_id cho note, n?u ch?a có thì dùng local note_id */
fun Context.getBackendNoteIdOrLocal(localNoteId: Long): String {
    val backendId = AIUserPreferences.getBackendNoteId(this, localNoteId)
    return backendId ?: localNoteId.toString()
}
