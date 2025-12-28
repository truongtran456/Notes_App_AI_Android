package com.philkes.notallyx.presentation.view.note

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ItemFormatToolbarBinding
import com.philkes.notallyx.presentation.view.misc.StylableEditTextWithHistory

class FormatToolbarAdapter(
    private val context: Context,
    private val editText: StylableEditTextWithHistory,
    @ColorInt color: Int?,
) : RecyclerView.Adapter<FormatToolbarAdapter.FormatViewHolder>() {

    enum class FormatAction(
        @DrawableRes val iconRes: Int
    ) {
        FONT(R.drawable.font),
        SIZE(R.drawable.size),
        ELLIPSE(R.drawable.ellipse20), // Note: file is ellipse20.png
        BOLD(R.drawable.bold),
        ITALIC(R.drawable.italic),
        UNDERLINE(R.drawable.underline),
        TEXT_LINE(R.drawable.textlight),
        TEXT_HIGHLIGHT(R.drawable.texthightlight),
        ALIGN_LEFT(R.drawable.alignleft),
        ALIGN_CENTER(R.drawable.alignmiddle),
        ALIGN_RIGHT(R.drawable.alignright),
        LIST(R.drawable.list);
        
        fun execute(adapter: FormatToolbarAdapter) {
            when (this) {
                FONT -> adapter.showFontDialog()
                SIZE -> adapter.showSizeDialog()
                ELLIPSE -> adapter.showEllipseDialog()
                BOLD -> adapter.toggleBold()
                ITALIC -> adapter.toggleItalic()
                UNDERLINE -> adapter.toggleUnderline()
                TEXT_LINE -> adapter.toggleStrikethrough()
                TEXT_HIGHLIGHT -> adapter.showHighlightDialog()
                ALIGN_LEFT -> adapter.alignLeft()
                ALIGN_CENTER -> adapter.alignCenter()
                ALIGN_RIGHT -> adapter.alignRight()
                LIST -> adapter.toggleList()
            }
        }
    }

    private val actions = FormatAction.entries.toList()
    private val colorInt = color

    override fun getItemCount() = actions.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormatViewHolder {
        val binding = ItemFormatToolbarBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FormatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FormatViewHolder, position: Int) {
        holder.bind(actions[position])
    }

    inner class FormatViewHolder(
        private val binding: ItemFormatToolbarBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(action: FormatAction) {
            binding.FormatIcon.setImageResource(action.iconRes)
            val iconColor = MaterialColors.getColor(
                binding.root,
                com.google.android.material.R.attr.colorOnSurface,
                ContextCompat.getColor(context, R.color.md_theme_onSurface)
            )
            binding.FormatIcon.setColorFilter(iconColor)
            binding.root.setOnClickListener {
                action.execute(this@FormatToolbarAdapter)
            }
        }
    }

    private fun toggleBold() {
        val selStart = editText.selectionStart
        val selEnd = editText.selectionEnd
        val hasBold = editText.getSpans(selStart, selEnd).any { it is StyleSpan && it.style == Typeface.BOLD }
        
        if (hasBold) {
            editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.BOLD)
        } else {
            editText.applySpan(StyleSpan(Typeface.BOLD))
        }
        notifyDataSetChanged()
    }

    private fun toggleItalic() {
        val selStart = editText.selectionStart
        val selEnd = editText.selectionEnd
        val hasItalic = editText.getSpans(selStart, selEnd).any { it is StyleSpan && it.style == Typeface.ITALIC }
        
        if (hasItalic) {
            editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.ITALIC)
        } else {
            editText.applySpan(StyleSpan(Typeface.ITALIC))
        }
        notifyDataSetChanged()
    }

    private fun toggleUnderline() {
        val selStart = editText.selectionStart
        val selEnd = editText.selectionEnd
        val hasUnderline = editText.getSpans(selStart, selEnd).any { it is UnderlineSpan }
        
        if (hasUnderline) {
            editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.UNDERLINE)
        } else {
            editText.applySpan(UnderlineSpan())
        }
        notifyDataSetChanged()
    }

    private fun toggleStrikethrough() {
        val selStart = editText.selectionStart
        val selEnd = editText.selectionEnd
        val hasStrikethrough = editText.getSpans(selStart, selEnd).any { it is StrikethroughSpan }
        
        if (hasStrikethrough) {
            editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.STRIKETHROUGH)
        } else {
            editText.applySpan(StrikethroughSpan())
        }
        notifyDataSetChanged()
    }

    private fun showHighlightDialog() {
        val colors = intArrayOf(
            Color.YELLOW, Color.CYAN, Color.GREEN, Color.MAGENTA,
            Color.RED, Color.BLUE, Color.GRAY, Color.TRANSPARENT
        )
        val colorNames = arrayOf(
            "Yellow", "Cyan", "Green", "Magenta",
            "Red", "Blue", "Gray", "None"
        )
        
        MaterialAlertDialogBuilder(context)
            .setTitle("Text Highlight")
            .setItems(colorNames) { _, which ->
                val selStart = editText.selectionStart
                val selEnd = editText.selectionEnd
                if (selStart == selEnd) {
                    editText.setSelection(selStart)
                }
                if (colors[which] == Color.TRANSPARENT) {
                    // Remove highlight - clear background color spans
                    val spans = editText.getSpans(selStart, selEnd)
                        .filterIsInstance<android.text.style.BackgroundColorSpan>()
                    spans.forEach { span ->
                        editText.changeTextWithHistory { text ->
                            text.removeSpan(span)
                        }
                    }
                } else {
                    editText.applySpan(android.text.style.BackgroundColorSpan(colors[which]), selStart, selEnd)
                }
            }
            .show()
    }

    private fun alignLeft() {
        // Alignment is typically handled at paragraph level
        // This is a placeholder - actual implementation depends on your text alignment system
    }

    private fun alignCenter() {
        // Alignment is typically handled at paragraph level
        // This is a placeholder - actual implementation depends on your text alignment system
    }

    private fun alignRight() {
        // Alignment is typically handled at paragraph level
        // This is a placeholder - actual implementation depends on your text alignment system
    }

    private fun toggleList() {
        val selStart = editText.selectionStart
        val selEnd = editText.selectionEnd
        val text = editText.text ?: return
        
        if (selStart == selEnd) {
            val textStr = text.toString()
            val lineStart = textStr.lastIndexOf('\n', selStart - 1) + 1
            val lineEnd = textStr.indexOf('\n', selStart).let { if (it == -1) text.length else it }
            
            val lineText = textStr.substring(lineStart, lineEnd)
            if (!lineText.startsWith("• ")) {
                editText.changeTextWithHistory { editable ->
                    editable.insert(lineStart, "• ")
                }
            }
        }
    }

    private fun showFontDialog() {
        // Placeholder - implement font selection dialog
        MaterialAlertDialogBuilder(context)
            .setTitle("Font")
            .setMessage("Font selection will be implemented")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSizeDialog() {
        val sizes = arrayOf("10", "12", "14", "16", "18", "20", "24", "28", "32")
        MaterialAlertDialogBuilder(context)
            .setTitle("Font Size")
            .setItems(sizes) { _, which ->
                // Placeholder - implement font size change
            }
            .show()
    }

    private fun showEllipseDialog() {
        // Placeholder - implement ellipse dialog
        MaterialAlertDialogBuilder(context)
            .setTitle("Ellipse")
            .setMessage("Ellipse options will be implemented")
            .setPositiveButton("OK", null)
            .show()
    }
}

