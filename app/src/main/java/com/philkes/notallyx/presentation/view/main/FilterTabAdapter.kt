package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ItemFilterTabBinding
import com.philkes.notallyx.presentation.getColorFromAttr

data class FilterTabItem(val label: String?)

class FilterTabAdapter(
    private val onTabClick: (String?) -> Unit
) : ListAdapter<FilterTabItem, FilterTabAdapter.FilterTabViewHolder>(FilterTabDiffCallback()) {

    private var selectedPosition = 0

    companion object {
        const val TAB_ALL = "ALL"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterTabViewHolder {
        val binding = ItemFilterTabBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FilterTabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FilterTabViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item.label, position == selectedPosition)
    }

    fun setSelectedPosition(position: Int) {
        val oldPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPosition)
        notifyItemChanged(selectedPosition)
    }

    inner class FilterTabViewHolder(
        private val binding: ItemFilterTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(label: String?, isSelected: Boolean) {
            val context = binding.root.context
            val text = if (label == TAB_ALL) {
                context.getString(R.string.all)
            } else {
                label ?: context.getString(R.string.all)
            }

            binding.FilterTabText.text = text

            // Style cho selected/unselected
            if (isSelected) {
                // Selected: background primary, text white
                binding.root.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.md_theme_primary)
                )
                binding.FilterTabText.setTextColor(
                    ContextCompat.getColor(context, R.color.md_theme_onPrimary)
                )
                binding.root.cardElevation = 4f
            } else {
                // Unselected: background surface container, text on surface
                binding.root.setCardBackgroundColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHighest)
                )
                binding.FilterTabText.setTextColor(
                    context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
                )
                binding.root.cardElevation = 2f
            }

            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    setSelectedPosition(position)
                    onTabClick(item.label)
                }
            }
        }
    }

    private class FilterTabDiffCallback : DiffUtil.ItemCallback<FilterTabItem>() {
        override fun areItemsTheSame(oldItem: FilterTabItem, newItem: FilterTabItem): Boolean {
            return oldItem.label == newItem.label
        }

        override fun areContentsTheSame(oldItem: FilterTabItem, newItem: FilterTabItem): Boolean {
            return oldItem == newItem
        }
    }
}

