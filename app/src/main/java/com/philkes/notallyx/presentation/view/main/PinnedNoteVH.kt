package com.philkes.notallyx.presentation.view.main

import android.util.TypedValue
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.model.hasUpcomingNotification
import com.philkes.notallyx.databinding.RecyclerPinnedNoteBinding
import com.philkes.notallyx.presentation.applySpans
import com.philkes.notallyx.presentation.bindLabels
import com.philkes.notallyx.presentation.displayFormattedTimestamp
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.extractColor
import com.philkes.notallyx.presentation.getColorFromAttr
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.setControlsContrastColorForAllViews
import com.philkes.notallyx.presentation.view.misc.ItemListener
import com.philkes.notallyx.presentation.viewmodel.preference.DateFormat
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortBy
import com.philkes.notallyx.presentation.viewmodel.preference.TextSize
import java.io.File

class PinnedNoteVH(
    private val binding: RecyclerPinnedNoteBinding,
    private val dateFormat: DateFormat,
    private val preferences: BaseNoteVHPreferences,
    listener: ItemListener,
) : RecyclerView.ViewHolder(binding.root) {

    // Cache drawables để tránh lag
    private val drawableCache = mutableMapOf<Int, android.graphics.drawable.Drawable?>()

    init {
        val title = preferences.textSize.displayTitleSize
        val body = preferences.textSize.displayBodySize

        binding.apply {
            Title.setTextSize(TypedValue.COMPLEX_UNIT_SP, title)
            Date.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)
            Note.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

            LinearLayout.children.forEach { view ->
                view as TextView
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)
            }

            Title.maxLines = 2 // Pinned notes chỉ hiển thị 2 dòng title
            Note.maxLines = 3 // Pinned notes chỉ hiển thị 3 dòng content

            // Không set click listener ở đây, sẽ được set trong PinnedNoteViewHolder
        }
    }

    fun updateCheck(checked: Boolean, color: String) {
        // Không dùng stroke của MaterialCardView để tránh bị cắt viền ở 4 góc
        binding.root.strokeWidth = 0
        binding.root.isChecked = checked
    }

    fun bind(baseNote: BaseNote, imageRoot: File?, checked: Boolean, sortBy: NotesSortBy) {
        updateCheck(checked, baseNote.color)

        when (baseNote.type) {
            Type.NOTE -> bindNote(baseNote.body, baseNote.spans, baseNote.title.isEmpty())
            Type.LIST -> bindList(baseNote.items, baseNote.title.isEmpty())
        }
        val (date, datePrefixResId) =
            when (sortBy) {
                NotesSortBy.CREATION_DATE -> Pair(baseNote.timestamp, R.string.creation_date)
                NotesSortBy.MODIFIED_DATE ->
                    Pair(baseNote.modifiedTimestamp, R.string.modified_date)
                else -> Pair(null, null)
            }
        binding.Date.displayFormattedTimestamp(date, dateFormat, datePrefixResId)

        setImages(baseNote.images, imageRoot)
        setFiles(baseNote.files)

        binding.Title.apply {
            text = baseNote.title
            isVisible = baseNote.title.isNotEmpty()
            updatePadding(
                bottom =
                    if (baseNote.hasNoContents() || shouldOnlyDisplayTitle(baseNote)) 0 else 8.dp
            )
            setCompoundDrawablesWithIntrinsicBounds(
                if (baseNote.type == Type.LIST && preferences.maxItems < 1)
                    R.drawable.checkbox_small
                else 0,
                0,
                0,
                0,
            )
        }

        if (preferences.hideLabels) {
            binding.LabelGroup.visibility = GONE
        } else {
            binding.LabelGroup.bindLabels(
                baseNote.labels,
                preferences.textSize,
                binding.Note.isVisible || binding.Title.isVisible,
            )
        }

        if (baseNote.isEmpty()) {
            binding.Title.apply {
                setText(baseNote.getEmptyMessage())
                isVisible = true
            }
        }
        setColor(baseNote.color, baseNote.id)

        binding.RemindersView.isVisible = baseNote.reminders.any { it.hasUpcomingNotification() }
    }

    private fun bindNote(body: String, spans: List<SpanRepresentation>, isTitleEmpty: Boolean) {
        binding.LinearLayout.visibility = GONE

        binding.Note.apply {
            text = body.applySpans(spans)
            if (preferences.maxLines < 1) {
                isVisible = isTitleEmpty
                maxLines = if (isTitleEmpty) 1 else 3
            } else {
                isVisible = body.isNotEmpty()
                maxLines = 3
            }
        }
    }

    private fun bindList(items: List<ListItem>, isTitleEmpty: Boolean) {
        binding.apply {
            Note.visibility = GONE
            if (items.isEmpty()) {
                LinearLayout.visibility = GONE
            } else {
                LinearLayout.visibility = VISIBLE
                val forceShowFirstItem = preferences.maxItems < 1 && isTitleEmpty
                val filteredList = items.take(if (forceShowFirstItem) 1 else 3) // Chỉ hiển thị 3 items cho pinned
                LinearLayout.children.forEachIndexed { index, view ->
                    if (view.id != R.id.ItemsRemaining) {
                        if (index < filteredList.size) {
                            val item = filteredList[index]
                            (view as TextView).apply {
                                text = item.body
                                handleChecked(this, item.checked)
                                visibility = VISIBLE
                                if (item.isChild) {
                                    updateLayoutParams<LinearLayout.LayoutParams> {
                                        marginStart = 20.dp
                                    }
                                }
                                if (index == filteredList.lastIndex) {
                                    updatePadding(bottom = 0)
                                }
                            }
                        } else view.visibility = GONE
                    }
                }

                if (preferences.maxItems > 0 && items.size > 3) {
                    ItemsRemaining.apply {
                        visibility = VISIBLE
                        text = (items.size - 3).toString()
                    }
                } else ItemsRemaining.visibility = GONE
            }
        }
    }

    private fun setColor(color: String, noteId: Long) {
        binding.root.apply {
            if (color == BaseNote.COLOR_DEFAULT) {
                // Sử dụng gradient distributor để phân phối gradient không trùng
                val gradientRes = GradientDistributor.getGradientForNote(noteId)
                
                // Cache drawable để tránh lag
                val drawable = drawableCache.getOrPut(gradientRes) {
                    context.getDrawable(gradientRes)
                }
                background = drawable
                setCardBackgroundColor(0) // Transparent để hiển thị gradient background
                setControlsContrastColorForAllViews(android.graphics.Color.WHITE) // Text màu trắng trên gradient
            } else {
                val colorInt = context.extractColor(color)
                setCardBackgroundColor(colorInt)
                setControlsContrastColorForAllViews(colorInt)
            }
        }
    }

    private fun setImages(images: List<FileAttachment>, mediaRoot: File?) {
        // Clear previous Glide request to prevent memory leaks
        clearGlideRequests()
        
        binding.apply {
            if (images.isNotEmpty()) {
                ImageView.visibility = VISIBLE
                Message.visibility = GONE

                val image = images[0]
                val file = if (mediaRoot != null) File(mediaRoot, image.localName) else null

                try {
                    Glide.with(ImageView)
                        .load(file)
                        .centerCrop()
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .listener(
                            object : RequestListener<android.graphics.drawable.Drawable> {
                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<android.graphics.drawable.Drawable>?,
                                    isFirstResource: Boolean,
                                ): Boolean {
                                    // Don't crash on error, just show message
                                    try {
                                        Message.visibility = VISIBLE
                                    } catch (e: Exception) {
                                        // Ignore if view is recycled
                                    }
                                    return false
                                }

                                override fun onResourceReady(
                                    resource: android.graphics.drawable.Drawable?,
                                    model: Any?,
                                    target: Target<android.graphics.drawable.Drawable>?,
                                    dataSource: DataSource?,
                                    isFirstResource: Boolean,
                                ): Boolean {
                                    return false
                                }
                            }
                        )
                        .into(ImageView)
                } catch (e: Exception) {
                    // Handle Glide errors gracefully to prevent crashes
                    Message.visibility = VISIBLE
                }
                
                if (images.size > 1) {
                    ImageViewMore.apply {
                        text = images.size.toString()
                        visibility = VISIBLE
                    }
                } else {
                    ImageViewMore.visibility = GONE
                }
            } else {
                ImageView.visibility = GONE
                Message.visibility = GONE
                ImageViewMore.visibility = GONE
                clearGlideRequests()
            }
        }
    }
    
    fun clearGlideRequests() {
        try {
            Glide.with(binding.ImageView).clear(binding.ImageView)
        } catch (e: Exception) {
            // Ignore errors if view is already recycled
        }
    }

    private fun setFiles(files: List<FileAttachment>) {
        binding.apply {
            if (files.isNotEmpty()) {
                FileViewLayout.visibility = VISIBLE
                FileView.text = files[0].originalName
                if (files.size > 1) {
                    FileViewMore.apply {
                        text = getQuantityString(R.plurals.more_files, files.size - 1)
                        visibility = VISIBLE
                    }
                } else {
                    FileViewMore.visibility = GONE
                }
            } else {
                FileViewLayout.visibility = GONE
            }
        }
    }

    private fun shouldOnlyDisplayTitle(baseNote: BaseNote) =
        when (baseNote.type) {
            Type.NOTE -> preferences.maxLines < 1
            Type.LIST -> preferences.maxItems < 1
        }

    private fun BaseNote.isEmpty() = title.isBlank() && hasNoContents() && images.isEmpty()

    private fun BaseNote.hasNoContents() = body.isEmpty() && items.isEmpty()

    private fun BaseNote.getEmptyMessage() =
        when (type) {
            Type.NOTE -> R.string.empty_note
            Type.LIST -> R.string.empty_list
        }

    private fun handleChecked(textView: TextView, checked: Boolean) {
        if (checked) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.checkbox_16,
                0,
                0,
                0,
            )
        } else
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.checkbox_outline_16,
                0,
                0,
                0,
            )
    }
}

