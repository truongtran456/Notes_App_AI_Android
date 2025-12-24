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

            // Style giống filter pills của Home Today
            val selectedDrawable =
                ContextCompat.getDrawable(context, R.drawable.bg_filter_pill_selected)
            val unselectedDrawable =
                ContextCompat.getDrawable(context, R.drawable.bg_filter_pill_unselected)

            if (isSelected) {
                binding.FilterTabText.background = selectedDrawable
                binding.FilterTabText.setTextColor(
                    ContextCompat.getColor(context, android.R.color.white)
                )
            } else {
                binding.FilterTabText.background = unselectedDrawable
                binding.FilterTabText.setTextColor(
                    ContextCompat.getColor(context, android.R.color.black)
                )
            }

            binding.FilterTabText.setOnClickListener {
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

