package com.philkes.notallyx.presentation.view.note

import android.content.Context
import android.text.Editable
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import androidx.annotation.ColorInt
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.view.misc.StylableEditTextWithHistory

class TextFormattingListsAdapter(
    private val context: Context,
    private val editText: StylableEditTextWithHistory,
    @ColorInt color: Int?,
    private val onUpdate: () -> Unit,
) : ToggleAdapter(mutableListOf(), color) {

    private val bulletList: Toggle =
        Toggle(R.string.bullet_list, R.drawable.format_list_bulleted, false) {
            applyListFormatting("• ")
            onUpdate()
        }
    
    private val numberedList: Toggle =
        Toggle(R.string.numbered_list, R.drawable.format_list_numbered, false) {
            applyListFormatting("1. ")
            onUpdate()
        }
    
    private val indent: Toggle =
        Toggle(R.string.indent, R.drawable.format_indent_increase, false) {
            applyIndent(true)
            onUpdate()
        }
    
    private val outdent: Toggle =
        Toggle(R.string.outdent, R.drawable.format_indent_decrease, false) {
            applyIndent(false)
            onUpdate()
        }

    init {
        toggles.addAll(listOf(bulletList, numberedList, indent, outdent))
    }
    
    private fun applyListFormatting(prefix: String) {
        val selStart = editText.selectionStart
        val selEnd = editText.selectionEnd
        val text = editText.text ?: return
        
        if (selStart == selEnd) {
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
    
    fun updateToggles(selStart: Int = editText.selectionStart, selEnd: Int = editText.selectionEnd) {
        // Lists adapter doesn't need to track state as bullet/numbered/indent are applied immediately
        // This method exists for consistency with other adapters
        notifyDataSetChanged()
    }
}

