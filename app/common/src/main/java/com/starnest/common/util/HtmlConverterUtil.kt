package com.starnest.common.util

import android.text.Spanned
import androidx.core.text.toSpannable
import com.onegravity.rteditor.api.format.RTFormat
import com.onegravity.rteditor.converter.ConverterHtmlToSpanned
import com.onegravity.rteditor.converter.ConverterSpannedToHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HtmlConverterUtil {
    private val htmlToSpanned = ConverterHtmlToSpanned()

    private val spannedToHtml = ConverterSpannedToHtml()

    suspend fun convertToSpanned(content: String): Spanned {
        return withContext(Dispatchers.IO) {
            return@withContext convertToSpannedSync(content)
        }
    }

    fun convertToSpannedSync(content: String): Spanned {
        return ConverterHtmlToSpanned().convertHtmlToSpannable(content).text.toSpannable()
    }

    fun convertToHtml(spanned: Spanned?): String {
        if (spanned == null) {
            return ""
        }
        return spannedToHtml.convert(spanned, RTFormat.Html.HTML).text
    }
}