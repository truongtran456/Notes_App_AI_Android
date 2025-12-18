package com.philkes.notallyx.presentation.view.note

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import androidx.annotation.ColorInt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.view.misc.StylableEditTextWithHistory

class TextFormattingFormatAdapter(
    private val context: Context,
    private val editText: StylableEditTextWithHistory,
    @ColorInt color: Int?,
    private val onUpdate: () -> Unit,
) : ToggleAdapter(mutableListOf(), color) {

    private val bold: Toggle =
        Toggle(R.string.bold, R.drawable.format_bold, false) {
            if (!it.checked) {
                editText.applySpan(StyleSpan(Typeface.BOLD))
            } else {
                editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.BOLD)
            }
            it.checked = !it.checked
            onUpdate()
        }
    
    private val italic: Toggle =
        Toggle(R.string.italic, R.drawable.format_italic, false) {
            if (!it.checked) {
                editText.applySpan(StyleSpan(Typeface.ITALIC))
            } else {
                editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.ITALIC)
            }
            it.checked = !it.checked
            onUpdate()
        }
    
    private val underline: Toggle =
        Toggle(R.string.underline, R.drawable.format_underline, false) {
            if (!it.checked) {
                editText.applySpan(UnderlineSpan())
            } else {
                editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.UNDERLINE)
            }
            it.checked = !it.checked
            onUpdate()
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
            onUpdate()
        }
    
    private val textColor: Toggle =
        Toggle(R.string.text_color, R.drawable.format_color_text, false) {
            showColorPickerDialog()
        }

    init {
        toggles.addAll(listOf(bold, italic, underline, strikethrough, textColor))
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
                    editText.setSelection(selStart)
                }
                editText.applySpan(ForegroundColorSpan(colors[which]), selStart, selEnd)
                onUpdate()
            }
            .show()
    }
    
    fun updateToggles(selStart: Int = editText.selectionStart, selEnd: Int = editText.selectionEnd) {
        var boldSpanFound = false
        var italicSpanFound = false
        var underlineSpanFound = false
        var strikethroughSpanFound = false
        
        editText.getSpans(selStart, selEnd).forEach { span ->
            when (span) {
                is StyleSpan -> {
                    when (span.style) {
                        Typeface.BOLD -> boldSpanFound = true
                        Typeface.ITALIC -> italicSpanFound = true
                    }
                }
                is UnderlineSpan -> underlineSpanFound = true
                is StrikethroughSpan -> strikethroughSpanFound = true
            }
        }
        bold.checked = boldSpanFound
        italic.checked = italicSpanFound
        underline.checked = underlineSpanFound
        strikethrough.checked = strikethroughSpanFound
        notifyDataSetChanged()
    }
}

