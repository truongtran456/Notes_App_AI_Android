package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ItemDayChipBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DayChip(
    val date: LocalDate,
    val isSelected: Boolean = false,
)

class DayChipAdapter(
    private val onSelect: (LocalDate) -> Unit,
) : ListAdapter<DayChip, DayChipAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDayChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onSelect)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemDayChipBinding,
        private val onSelect: (LocalDate) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dowFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        private val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        
        // Cache drawables to avoid repeated ContextCompat.getDrawable() calls
        private var selectedDrawable: android.graphics.drawable.Drawable? = null
        private var unselectedDrawable: android.graphics.drawable.Drawable? = null
        private var drawablesInitialized = false

        fun bind(chip: DayChip) {
            binding.DayOfWeek.text = chip.date.format(dowFormatter)
            binding.DayOfMonth.text = chip.date.dayOfMonth.toString()
            binding.Month.text = chip.date.format(monthFormatter)

            val ctx = binding.root.context
            val selected = chip.isSelected
            
            // Initialize drawables once
            if (!drawablesInitialized) {
                selectedDrawable = ContextCompat.getDrawable(ctx, R.drawable.bg_day_chip_selected)
                unselectedDrawable = ContextCompat.getDrawable(ctx, R.drawable.bg_day_chip_unselected)
                drawablesInitialized = true
            }
            
            binding.root.background = if (selected) selectedDrawable else unselectedDrawable
            
            val primary = if (selected) android.graphics.Color.WHITE else 0xFF1A1A1A.toInt()
            val secondary = if (selected) 0xFFEEEEEE.toInt() else 0xFF5A5A5A.toInt()
            binding.DayOfWeek.setTextColor(primary)
            binding.DayOfMonth.setTextColor(primary)
            binding.Month.setTextColor(if (selected) primary else secondary)

            binding.root.setOnClickListener {
                onSelect(chip.date)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DayChip>() {
        override fun areItemsTheSame(oldItem: DayChip, newItem: DayChip): Boolean =
            oldItem.date == newItem.date

        override fun areContentsTheSame(oldItem: DayChip, newItem: DayChip): Boolean =
            oldItem == newItem
    }
}

