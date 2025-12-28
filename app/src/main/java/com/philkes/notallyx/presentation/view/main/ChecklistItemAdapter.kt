package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.databinding.ItemChecklistItemBinding

class ChecklistItemAdapter(
    private val textSize: Float,
    private val handleChecked: (TextView, Boolean) -> Unit
) : ListAdapter<ListItem, ChecklistItemAdapter.ChecklistItemViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChecklistItemViewHolder {
        val binding = ItemChecklistItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChecklistItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChecklistItemViewHolder, position: Int) {
        holder.bind(getItem(position), textSize, handleChecked)
    }

    class ChecklistItemViewHolder(
        private val binding: ItemChecklistItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ListItem, textSize: Float, handleChecked: (TextView, Boolean) -> Unit) {
            binding.root.apply {
                text = item.body
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSize)
                setTextColor(android.graphics.Color.BLACK)
                
                // QUAN TRỌNG: Tắt khả năng click của TextView để không chặn click events của card
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
                
                handleChecked(this, item.checked)
                
                // Set margin cho child items
                val layoutParams = layoutParams as ViewGroup.MarginLayoutParams
                if (item.isChild) {
                    layoutParams.marginStart = 20.dp(binding.root.context)
                } else {
                    layoutParams.marginStart = 0
                }
                this.layoutParams = layoutParams
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem.body == newItem.body &&
                   oldItem.checked == newItem.checked &&
                   oldItem.isChild == newItem.isChild
        }
    }
}

fun Int.dp(context: android.content.Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}

