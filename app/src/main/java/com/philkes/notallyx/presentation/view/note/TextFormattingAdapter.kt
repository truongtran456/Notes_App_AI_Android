package com.philkes.notallyx.presentation.view.note

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.view.misc.StylableEditTextWithHistory

class TextFormattingAdapter(
    private val context: Context,
    private val editText: StylableEditTextWithHistory,
    @ColorInt color: Int?,
) : ToggleAdapter(mutableListOf(), color) {

    private var link: Toggle =
        Toggle(R.string.link, R.drawable.link, false) {
            if (!it.checked) {
                editText.showAddLinkDialog(
                    context = context,
                    onClose = { updateTextFormattingToggles() },
                )
            } else {
                val spans = editText.getSpans(type = StylableEditTextWithHistory.TextStyleType.LINK)
                spans.firstOrNull()?.let { urlSpan ->
                    editText.showEditDialog((urlSpan as URLSpan)) { updateTextFormattingToggles() }
                }
            }
            it.checked = !it.checked
            notifyItemChanged(0)
        }
    private val bold: Toggle =
        Toggle(R.string.bold, R.drawable.format_bold, false) {
            if (!it.checked) {
                editText.applySpan(StyleSpan(Typeface.BOLD))
            } else {
                editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.BOLD)
            }
            it.checked = !it.checked
            notifyItemChanged(1)
        }
    private val italic: Toggle =
        Toggle(R.string.italic, R.drawable.format_italic, false) {
            if (!it.checked) {
                editText.applySpan(StyleSpan(Typeface.ITALIC))
            } else {
                editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.ITALIC)
            }
            it.checked = !it.checked
            notifyItemChanged(2)
        }
    private val strikethrough: Toggle =
        Toggle(R.string.strikethrough, R.drawable.format_strikethrough, false) {
            if (!it.checked) {
                editText.applySpan(StrikethroughSpan())
            } else {
                editText.clearFormatting(
                    type = StylableEditTextWithHistory.TextStyleType.STRIKETHROUGH
                )
            }
            it.checked = !it.checked
            notifyItemChanged(3)
        }
    private val monospace: Toggle =
        Toggle(R.string.monospace, R.drawable.code, false) {
            if (!it.checked) {
                editText.applySpan(TypefaceSpan("monospace"))
            } else {
                editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.MONOSPACE)
            }
            it.checked = !it.checked
            notifyItemChanged(4)
        }
    private val title: Toggle =
        Toggle(R.string.title, R.drawable.format_title, false) {
            applyHeadingStyle(1.5f, StylableEditTextWithHistory.TextStyleType.TITLE)
            updateTextFormattingToggles()
        }
    
    private val heading: Toggle =
        Toggle(R.string.heading, R.drawable.format_heading, false) {
            applyHeadingStyle(1.25f, StylableEditTextWithHistory.TextStyleType.HEADING)
            updateTextFormattingToggles()
        }
    
    private val body: Toggle =
        Toggle(R.string.body, R.drawable.format_bold, false) {
            applyHeadingStyle(1.0f, StylableEditTextWithHistory.TextStyleType.BODY)
            updateTextFormattingToggles()
        }
    
    private val underline: Toggle =
        Toggle(R.string.underline, R.drawable.format_underline, false) {
            if (!it.checked) {
                editText.applySpan(UnderlineSpan())
            } else {
                editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.UNDERLINE)
            }
            it.checked = !it.checked
            updateTextFormattingToggles()
        }
    
    private val bulletList: Toggle =
        Toggle(R.string.bullet_list, R.drawable.format_list_bulleted, false) {
            applyListFormatting("• ")
            updateTextFormattingToggles()
        }
    
    private val numberedList: Toggle =
        Toggle(R.string.numbered_list, R.drawable.format_list_numbered, false) {
            applyListFormatting("1. ")
            updateTextFormattingToggles()
        }
    
    private val textColor: Toggle =
        Toggle(R.string.text_color, R.drawable.format_color_text, false) {
            showColorPickerDialog()
        }
    
    private val indent: Toggle =
        Toggle(R.string.indent, R.drawable.format_indent_increase, false) {
            applyIndent(true)
            updateTextFormattingToggles()
        }
    
    private val outdent: Toggle =
        Toggle(R.string.outdent, R.drawable.format_indent_decrease, false) {
            applyIndent(false)
            updateTextFormattingToggles()
        }
    
    private val clearFormat: Toggle =
        Toggle(R.string.clear_formatting, R.drawable.format_clear, false) {
            editText.clearFormatting()
            updateTextFormattingToggles()
        }

    init {
        toggles.addAll(listOf(
            title, heading, body,
            bold, italic, underline,
            bulletList, numberedList,
            textColor, indent, outdent,
            link, strikethrough, monospace, clearFormat
        ))
    }
    
    private fun applyHeadingStyle(sizeChange: Float, type: StylableEditTextWithHistory.TextStyleType) {
        val selStart = editText.selectionStart
        val selEnd = editText.selectionEnd
        
        // Clear existing heading styles
        editText.clearFormatting(selStart, selEnd, StylableEditTextWithHistory.TextStyleType.TITLE)
        editText.clearFormatting(selStart, selEnd, StylableEditTextWithHistory.TextStyleType.HEADING)
        editText.clearFormatting(selStart, selEnd, StylableEditTextWithHistory.TextStyleType.BODY)
        
        if (selStart == selEnd) {
            // No selection, apply to current line
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
    
    private fun applyListFormatting(prefix: String) {
        val selStart = editText.selectionStart
        val selEnd = editText.selectionEnd
        val text = editText.text ?: return
        
        if (selStart == selEnd) {
            // No selection, apply to current line
            val textStr = text.toString()
            val lineStart = textStr.lastIndexOf('\n', selStart - 1) + 1
            val lineEnd = textStr.indexOf('\n', selStart).let { if (it == -1) text.length else it }
            
            val lineText = textStr.substring(lineStart, lineEnd)
            if (!lineText.startsWith(prefix)) {
                editText.changeTextWithHistory { editable ->
                    editable.insert(lineStart, prefix)
                }
            }
        } else {
            // Apply to each line in selection
            val textStr = text.toString()
            val selectedText = textStr.substring(selStart, selEnd)
            val lines = selectedText.split('\n')
            var currentPos = selStart
            editText.changeTextWithHistory { editable ->
                lines.forEachIndexed { index, line ->
                    val lineStartInEditable = currentPos
                    if (!line.startsWith(prefix) && line.isNotEmpty()) {
                        editable.insert(lineStartInEditable, prefix)
                        currentPos += prefix.length
                    }
                    currentPos += line.length + if (index < lines.size - 1) 1 else 0
                }
            }
        }
    }
    
    private fun applyIndent(increase: Boolean) {
        val selStart = editText.selectionStart
        val selEnd = editText.selectionEnd
        val text = editText.text ?: return
        
        if (selStart == selEnd) {
            val textStr = text.toString()
            val lineStart = textStr.lastIndexOf('\n', selStart - 1) + 1
            val lineEnd = textStr.indexOf('\n', selStart).let { if (it == -1) text.length else it }
            
            val existingSpans = text.getSpans(lineStart, lineEnd, LeadingMarginSpan::class.java)
            val currentMargin = existingSpans.firstOrNull()?.let { span ->
                // LeadingMarginSpan.Standard doesn't expose leadingMargin directly
                // We'll use a default value and track it ourselves, or use reflection
                try {
                    val field = span.javaClass.getDeclaredField("mLeading")
                    field.isAccessible = true
                    field.getInt(span)
                } catch (e: Exception) {
                    0
                }
            } ?: 0
            val newMargin = if (increase) currentMargin + 40 else maxOf(0, currentMargin - 40)
            
            editText.changeTextWithHistory { editable ->
                existingSpans.forEach { editable.removeSpan(it) }
                if (newMargin > 0) {
                    editable.setSpan(LeadingMarginSpan.Standard(newMargin, 0), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        } else {
            // Apply to each line in selection
            val textStr = text.toString()
            val selectedText = textStr.substring(selStart, selEnd)
            val lines = selectedText.split('\n')
            var currentPos = selStart
            editText.changeTextWithHistory { editable ->
                lines.forEachIndexed { index, line ->
                    val lineStart = currentPos
                    val lineEnd = currentPos + line.length
                    
                    val existingSpans = editable.getSpans(lineStart, lineEnd, LeadingMarginSpan::class.java)
                    val currentMargin = existingSpans.firstOrNull()?.let { span ->
                        try {
                            val field = span.javaClass.getDeclaredField("mLeading")
                            field.isAccessible = true
                            field.getInt(span)
                        } catch (e: Exception) {
                            0
                        }
                    } ?: 0
                    val newMargin = if (increase) currentMargin + 40 else maxOf(0, currentMargin - 40)
                    
                    existingSpans.forEach { editable.removeSpan(it) }
                    if (newMargin > 0) {
                        editable.setSpan(LeadingMarginSpan.Standard(newMargin, 0), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    
                    currentPos += line.length + if (index < lines.size - 1) 1 else 0
                }
            }
        }
    }
    
    private fun showColorPickerDialog() {
        val colors = intArrayOf(
            Color.BLACK, Color.RED, Color.BLUE, Color.GREEN,
            Color.YELLOW, Color.MAGENTA, Color.CYAN, Color.GRAY
        )
        val colorNames = arrayOf(
            "Black", "Red", "Blue", "Green",
            "Yellow", "Magenta", "Cyan", "Gray"
        )
        
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.text_color)
            .setItems(colorNames) { _, which ->
                val selStart = editText.selectionStart
                val selEnd = editText.selectionEnd
                if (selStart == selEnd) {
                    // No selection, apply to current position (will apply to next typed text)
                    editText.setSelection(selStart)
                }
                editText.applySpan(ForegroundColorSpan(colors[which]), selStart, selEnd)
                updateTextFormattingToggles()
            }
            .show()
    }

    internal fun updateTextFormattingToggles(
        selStart: Int = editText.selectionStart,
        selEnd: Int = editText.selectionEnd,
    ) {
        var boldSpanFound = false
        var italicSpanFound = false
        var linkSpanFound = false
        var monospaceSpanFound = false
        var strikethroughSpanFound = false
        var underlineSpanFound = false
        var titleSpanFound = false
        var headingSpanFound = false
        var bodySpanFound = false
        
        editText.getSpans(selStart, selEnd).forEach { span ->
            when (span) {
                is StyleSpan -> {
                    when (span.style) {
                        Typeface.BOLD -> boldSpanFound = true
                        Typeface.ITALIC -> italicSpanFound = true
                    }
                }
                is URLSpan -> linkSpanFound = true
                is TypefaceSpan -> if (span.family == "monospace") monospaceSpanFound = true
                is StrikethroughSpan -> strikethroughSpanFound = true
                is UnderlineSpan -> underlineSpanFound = true
                is RelativeSizeSpan -> {
                    when {
                        span.sizeChange >= 1.4f -> titleSpanFound = true
                        span.sizeChange >= 1.2f -> headingSpanFound = true
                        else -> bodySpanFound = true
                    }
                }
            }
        }
        bold.checked = boldSpanFound
        italic.checked = italicSpanFound
        link.checked = linkSpanFound
        monospace.checked = monospaceSpanFound
        strikethrough.checked = strikethroughSpanFound
        underline.checked = underlineSpanFound
        title.checked = titleSpanFound
        heading.checked = headingSpanFound && !titleSpanFound
        body.checked = bodySpanFound && !titleSpanFound && !headingSpanFound
        notifyDataSetChanged()
    }
}
