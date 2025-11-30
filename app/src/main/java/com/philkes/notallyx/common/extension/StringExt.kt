package com.philkes.notallyx.common.extension

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.util.Patterns
import android.webkit.MimeTypeMap
import androidx.annotation.ColorInt
import androidx.core.graphics.toColorInt
import java.text.SimpleDateFormat
import java.util.*

fun String?.indexesOf(subString: String, ignoreCase: Boolean = true): List<Int> {
    return this?.let {
        val regex = if (ignoreCase) Regex(subString, RegexOption.IGNORE_CASE) else Regex(subString)
        regex.findAll(this).map { it.range.first }.toList()
    } ?: emptyList()
}

fun String?.toDate(format: String): Date? {
    val dateFormatter = SimpleDateFormat(format, Locale.ENGLISH)
    return this?.let {
        dateFormatter.parse(it)
    }
}

fun String?.isNullOrEmpty(): Boolean {
    if (this == null) {
        return true
    }

    return this.isEmpty()
}

fun String?.nullToZero() = this ?: "0"

fun String?.nullToEmpty() = this ?: ""

fun String.bounds(paint: Paint): Rect {
    val bounds = Rect()

    paint.getTextBounds(this, 0, this.length, bounds)

    return bounds
}

fun String.isValidUrl(): Boolean {
    return Patterns.WEB_URL.matcher(this).matches()
}

fun String.toDrawable(context: Context, subfolder: String?): Drawable? {
    return try {
        val inputStream = if (subfolder != null) {
            context.assets.open("$subfolder/$this")
        } else {
            context.assets.open(this)
        }

        Drawable.createFromStream(inputStream, null)
    } catch (e: Exception) {
        null
    }
}

val String.colorOrNull: Int?
    @ColorInt
    get() = try {
        if (this.isEmpty()) {
            null
        } else {
            this.toColorInt()
        }
    } catch (e: Exception) {
        null
    }


fun String.ensureValidColor(): String {
    if (length == 8) {
        return replace("#", "#0")
    }

    return this
}

val String.color: Int
    @ColorInt
    get() = try {
        if (this.isEmpty()) {
            0
        } else {
            ensureValidColor().toColorInt()
        }
    } catch (e: Exception) {
        0
    }

val String.mimeType: String?
    get() {
        var type: String? = null
        val extension = MimeTypeMap.getFileExtensionFromUrl(this)
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        return type
    }

val String.fileExtension: String
    get() {
        val extension = MimeTypeMap.getFileExtensionFromUrl(this)
        if (extension == null || extension == "") {
            return split(".").lastOrNull() ?: ""
        }
        return extension
    }

fun String.random(length: Int) : String {
    var output = ""
    for (i in 1..length) {
        output += random()
    }
    return output
}

fun String.hostName() : String {
    return split("/").firstOrNull { it.isNotEmpty() && !it.contains("http") }?.removePrefix(prefix = "www.") ?: this
}

val String.urlEncoded: String
    get() = replace("+", "%20").replace("*", "%2A").replace(" ", "%20")

fun String.toUri(): Uri = Uri.parse(this)

fun CharSequence.toSpannable(): Spannable = SpannableString.valueOf(this)

