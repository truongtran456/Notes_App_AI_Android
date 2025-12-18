package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ItemFilterPillBinding
import com.philkes.notallyx.presentation.activity.main.fragment.HomeFragment

data class HomeFilterPillItem(
    val type: HomeFragment.FilterType,
    val label: String
)

class HomeFilterPillAdapter(
    private val onFilterClick: (HomeFragment.FilterType) -> Unit
) : ListAdapter<HomeFilterPillItem, HomeFilterPillAdapter.FilterPillViewHolder>(FilterPillDiffCallback()) {

    private var selectedType: HomeFragment.FilterType = HomeFragment.FilterType.ALL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterPillViewHolder {
        val binding = ItemFilterPillBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FilterPillViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FilterPillViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, item.type == selectedType)
    }

    fun setSelectedType(type: HomeFragment.FilterType) {
        val oldType = selectedType
        selectedType = type
        currentList.forEachIndexed { index, item ->
            if (item.type == oldType || item.type == type) {
                notifyItemChanged(index)
            }
        }
    }

    inner class FilterPillViewHolder(
        private val binding: ItemFilterPillBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        // Cache drawables and colors to avoid repeated ContextCompat calls
        private var selectedDrawable: android.graphics.drawable.Drawable? = null
        private var unselectedDrawable: android.graphics.drawable.Drawable? = null
        private var selectedTextColor: Int? = null
        private var unselectedTextColor: Int? = null
        private var drawablesInitialized = false

        fun bind(item: HomeFilterPillItem, isSelected: Boolean) {
            binding.FilterPill.text = item.label

            // Initialize drawables and colors once
            if (!drawablesInitialized) {
                val ctx = binding.root.context
                selectedDrawable = ContextCompat.getDrawable(ctx, R.drawable.bg_filter_pill_selected)
                unselectedDrawable = ContextCompat.getDrawable(ctx, R.drawable.bg_filter_pill_unselected)
                selectedTextColor = ContextCompat.getColor(ctx, android.R.color.white)
                unselectedTextColor = ContextCompat.getColor(ctx, android.R.color.black)
                drawablesInitialized = true
            }

            if (isSelected) {
                binding.FilterPill.background = selectedDrawable
                binding.FilterPill.setTextColor(selectedTextColor ?: android.graphics.Color.WHITE)
            } else {
                binding.FilterPill.background = unselectedDrawable
                binding.FilterPill.setTextColor(unselectedTextColor ?: android.graphics.Color.BLACK)
            }

            binding.root.setOnClickListener {
                onFilterClick(item.type)
            }
        }
    }

    private class FilterPillDiffCallback : DiffUtil.ItemCallback<HomeFilterPillItem>() {
        override fun areItemsTheSame(oldItem: HomeFilterPillItem, newItem: HomeFilterPillItem): Boolean {
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: HomeFilterPillItem, newItem: HomeFilterPillItem): Boolean {
            return oldItem == newItem
        }
    }
}

