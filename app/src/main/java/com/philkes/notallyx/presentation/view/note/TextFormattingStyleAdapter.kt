package com.philkes.notallyx.presentation.view.note

import android.content.Context
import android.graphics.Typeface
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.annotation.ColorInt
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.view.misc.StylableEditTextWithHistory

class TextFormattingStyleAdapter(
    private val context: Context,
    private val editText: StylableEditTextWithHistory,
    @ColorInt color: Int?,
    private val onUpdate: () -> Unit,
) : ToggleAdapter(mutableListOf(), color) {

    private val title: Toggle =
        Toggle(R.string.title, R.drawable.format_title, false) {
            applyHeadingStyle(1.5f, StylableEditTextWithHistory.TextStyleType.TITLE)
            onUpdate()
        }
    
    private val heading: Toggle =
        Toggle(R.string.heading, R.drawable.format_heading, false) {
            applyHeadingStyle(1.25f, StylableEditTextWithHistory.TextStyleType.HEADING)
            onUpdate()
        }
    
    private val body: Toggle =
        Toggle(R.string.body, R.drawable.format_bold, false) {
            applyHeadingStyle(1.0f, StylableEditTextWithHistory.TextStyleType.BODY)
            onUpdate()
        }

    init {
        toggles.addAll(listOf(title, heading, body))
    }
    
    private fun applyHeadingStyle(sizeChange: Float, type: StylableEditTextWithHistory.TextStyleType) {
        val selStart = editText.selectionStart
        val selEnd = editText.selectionEnd
        
        // Clear existing heading styles
        editText.clearFormatting(selStart, selEnd, StylableEditTextWithHistory.TextStyleType.TITLE)
        editText.clearFormatting(selStart, selEnd, StylableEditTextWithHistory.TextStyleType.HEADING)
        editText.clearFormatting(selStart, selEnd, StylableEditTextWithHistory.TextStyleType.BODY)
        
        if (selStart == selEnd) {
            val text = editText.text ?: return
            val textStr = text.toString()
            val lineStart = textStr.lastIndexOf('\n', selStart - 1) + 1
            val lineEnd = textStr.indexOf('\n', selStart).let { if (it == -1) text.length else it }
            editText.applySpan(RelativeSizeSpan(sizeChange), lineStart, lineEnd)
            editText.applySpan(StyleSpan(Typeface.BOLD), lineStart, lineEnd)
        } else {
            editText.applySpan(RelativeSizeSpan(sizeChange), selStart, selEnd)
            editText.applySpan(StyleSpan(Typeface.BOLD), selStart, selEnd)
        }
    }
    
    fun updateToggles(selStart: Int = editText.selectionStart, selEnd: Int = editText.selectionEnd) {
        var titleSpanFound = false
        var headingSpanFound = false
        var bodySpanFound = false
        
        editText.getSpans(selStart, selEnd).forEach { span ->
            if (span is RelativeSizeSpan) {
                when {
                    span.sizeChange >= 1.4f -> titleSpanFound = true
                    span.sizeChange >= 1.2f -> headingSpanFound = true
                    else -> bodySpanFound = true
                }
            }
        }
        title.checked = titleSpanFound
        heading.checked = headingSpanFound && !titleSpanFound
        body.checked = bodySpanFound && !titleSpanFound && !headingSpanFound
        notifyDataSetChanged()
    }
}

