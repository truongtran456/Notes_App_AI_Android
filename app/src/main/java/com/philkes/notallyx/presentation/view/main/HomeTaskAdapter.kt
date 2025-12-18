package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.databinding.ItemHomeTaskCardBinding
import java.text.SimpleDateFormat
import java.util.*

enum class IconType {
    ARROW_DIAGONAL, PAUSE, PLAY
}

class HomeTaskAdapter(
    private val onNoteClick: (BaseNote) -> Unit
) : ListAdapter<BaseNote, HomeTaskViewHolder>(HomeTaskDiffCallback()) {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_FILTER = 1
        const val VIEW_TYPE_TASK = 2
    }

    private val gradientDrawables = listOf(
        com.philkes.notallyx.R.drawable.bg_task_card_gradient_1,
        com.philkes.notallyx.R.drawable.bg_task_card_gradient_2,
        com.philkes.notallyx.R.drawable.bg_task_card_gradient_3,
        com.philkes.notallyx.R.drawable.bg_task_card_gradient_4,
        com.philkes.notallyx.R.drawable.bg_task_card_gradient_5,
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeTaskViewHolder {
        val binding = ItemHomeTaskCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HomeTaskViewHolder(binding, onNoteClick)
    }

    override fun onBindViewHolder(holder: HomeTaskViewHolder, position: Int) {
        val note = getItem(position)
        val gradientIndex = position % gradientDrawables.size
        // Icon type: arrow diagonal cho card đầu, pause cho card thứ 2, arrow cho các card khác
        val iconType = when (position % 3) {
            0 -> IconType.ARROW_DIAGONAL
            1 -> IconType.PAUSE
            else -> IconType.ARROW_DIAGONAL
        }
        holder.bind(note, gradientDrawables[gradientIndex], iconType)
    }
}

class HomeTaskViewHolder(
    private val binding: ItemHomeTaskCardBinding,
    private val onNoteClick: (BaseNote) -> Unit
) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

    private val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    
    // Cache drawables to avoid repeated getDrawable() calls
    private val drawableCache = mutableMapOf<Int, android.graphics.drawable.Drawable?>()
    
    init {
        // Set locale to English for consistent formatting
        dateFormat.applyPattern("dd MMM, yyyy")
        timeFormat.applyPattern("hh:mm a")
    }

    fun bind(note: BaseNote, gradientDrawable: Int, iconType: IconType = IconType.ARROW_DIAGONAL) {
        binding.apply {
            // Set gradient background cho MaterialCardView
            CardRoot.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            // Cache drawable to avoid repeated getDrawable() calls
            val drawable = drawableCache.getOrPut(gradientDrawable) {
                root.context.getDrawable(gradientDrawable)
            }
            CardRoot.background = drawable
            
            // Set date and time
            val date = Date(note.modifiedTimestamp)
            DateText.text = dateFormat.format(date)
            TimeText.text = timeFormat.format(date)
            
            // Set title
            TitleText.text = note.title.ifEmpty { 
                root.context.getString(com.philkes.notallyx.R.string.empty_note)
            }
            
            // Set description
            DescriptionText.text = when {
                note.type == com.philkes.notallyx.data.model.Type.LIST && note.items.isNotEmpty() -> {
                    // Hiển thị checklist items với bullet
                    note.items.take(3).joinToString(" ") { item ->
                        "• ${item.body.take(50)}"
                    }
                }
                note.body.isNotBlank() -> {
                    // Hiển thị body text
                    val bodyLines = note.body.lines().filter { it.isNotBlank() }
                    if (bodyLines.isNotEmpty()) {
                        bodyLines.first().take(100)
                    } else {
                        ""
                    }
                }
                else -> ""
            }
            
            // Show status if note has reminders
            if (note.reminders.isNotEmpty()) {
                StatusText.visibility = android.view.View.VISIBLE
                StatusText.text = "Reminder"
            } else {
                StatusText.visibility = android.view.View.GONE
            }
            
            // Set icon based on type
            when (iconType) {
                IconType.ARROW_DIAGONAL -> {
                    ActionButton.setImageResource(com.philkes.notallyx.R.drawable.ic_arrow_diagonal)
                }
                IconType.PAUSE -> {
                    ActionButton.setImageResource(com.philkes.notallyx.R.drawable.ic_pause)
                }
                IconType.PLAY -> {
                    // TODO: Add play icon if needed
                    ActionButton.setImageResource(com.philkes.notallyx.R.drawable.ic_arrow_diagonal)
                }
            }
            
            // Set click listener
            root.setOnClickListener {
                onNoteClick(note)
            }
            
            ActionButtonContainer.setOnClickListener {
                onNoteClick(note)
            }
        }
    }
}

class HomeTaskDiffCallback : DiffUtil.ItemCallback<BaseNote>() {
    override fun areItemsTheSame(oldItem: BaseNote, newItem: BaseNote): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: BaseNote, newItem: BaseNote): Boolean {
        return oldItem == newItem
    }
}

