package com.starnest.common.extension

import android.content.Context
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import java.util.regex.Pattern


val String.urlEncoded: String
    get() = replace("+", "%20").replace("*", "%2A").replace(" ", "%20")

fun String.toUri(): Uri = Uri.parse(this)

fun CharSequence.toSpannable(): Spannable = SpannableString.valueOf(this)

fun String.youTubeVideoId(): String? {
    val pattern =
        "^(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com/(?:.*v=|.*\\/|embed\\/|v\\/)|youtu\\.be/)([a-zA-Z0-9_-]{11}).*"
    val regex = Pattern.compile(pattern)
    val matcher = regex.matcher(this)

    return if (matcher.find()) matcher.group(1) else null
}


fun String.setBoldBetweenAsterisk(): String {
    val regex = "(?s)\\*\\*?(.+?)\\*\\*".toRegex()

    var newText = this

    regex.findAll(this).toMutableList().reversed().onEach {
        newText = newText.replace(
            it.value,
            "<b>${it.value.replace("**", "")}</b>"
        )
    }

    val html = newText.replace(
        "\n", "<br>"
    )

    return html.parseLink()
}

fun String.parseLink(): String {
    val linkAndDescriptionRegex = "\\[(.*?)\\]\\((https?://[^\\s]+)\\)".toRegex()
    val linkRegex = "\\((https?://[^\\s]+)\\)".toRegex()
    val descriptionRegex = "\\[(.*?)\\]".toRegex()

    var newText = this
    linkAndDescriptionRegex.findAll(this).toMutableList().reversed().onEach {
        val link = linkRegex.find(it.value)?.value?.replace("(", "")?.replace(")", "")
        var description = descriptionRegex.find(it.value)?.value?.replace("[", "")?.replace("]", "")

        if (description.equals("link", true)) {
            description = link
        }
        newText = newText.replace(
            it.value,
            "<a href=\"$link\">$description</a>"
        )
    }

    return newText
}


fun String.isEndBeDot(): Boolean {
    return findLastIndexOfDot(0) == lastIndex
}

fun String.findLastIndexOfDot(start: Int): Int {
    val indexOfDot = indexOfLast { it == '.' }
    val indexOfExMark = indexOfLast { it == '!' }
    val indexOfQuestionMark = indexOfLast { it == '?' }
    val indexOfThreeDot = indexOfLast { it == '…' }

    val maxIndex = maxOf(
        indexOfExMark,
        indexOfDot,
        indexOfQuestionMark,
        indexOfThreeDot
    ).takeIf { it >= start } ?: -1

    return maxIndex
}

fun String.removeIfLastCharacterIsDot(): String {
    return if (findLastIndexOfDot(length - 1) == (length - 1)) {
        substring(0, length - 1)
    } else this
}



fun String.template(params: Map<String, String?>): String {
    var output = this
    for (item in params.entries) {
        item.value?.let {
            output = output.replace(item.key, it)
        }
    }

    Log.d("buildAskContent", output)
    return output
}


fun String.containMathJax(): Boolean {
    val mathJaxPatterns = listOf(
        "\\$.*?\\$",          // Inline or block math
        "\\\\\\(.*?\\\\\\)",  // Escaped inline math
        "\\\\\\[.*?\\\\\\]",  // Escaped display math
        "\\$\\$.*?\\$\\$",    // Block math
    )

    return mathJaxPatterns.any { pattern -> Regex(pattern).containsMatchIn(this) }
}


fun TextView.setURLClick(action: (Uri) -> Unit) {
    text = SpannableStringBuilder.valueOf(text).apply {
        getSpans(0, length, URLSpan::class.java).forEach {
            setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        try {
                            action(Uri.parse(it.url))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                getSpanStart(it),
                getSpanEnd(it),
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            removeSpan(it)
        }
    }
}

fun String.getExploreAssetUri(context: Context): Uri? {
    val assetPath = "file:///android_asset/template/$this.png"
    return try {
        context.assets.open("template/$this.png").close()
        Uri.parse(assetPath)
    } catch (e: Exception) {
        null
    }
}

fun String.getTemplateUri(): Uri? {
    val assetPath = "file:///android_asset/template/$this"
    return Uri.parse(assetPath)
}

fun String.getLessonBackgroundUri() : Uri? {
    val assetPath = "file:///android_asset/lesson/$this"
    return Uri.parse(assetPath)
}
