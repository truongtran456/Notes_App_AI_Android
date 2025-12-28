package com.philkes.notallyx.presentation.view.vocab

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ItemVocabStatBinding

data class VocabStatItem(
    val vocab: String,
    val earnedPoints: Int,
    val maxPoints: Int,
    val percentage: Int
)

class VocabStatAdapter : ListAdapter<VocabStatItem, VocabStatAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(
        private val binding: ItemVocabStatBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(stat: VocabStatItem) {
            binding.apply {
                // Title
                VocabTitle.text = stat.vocab.replaceFirstChar { 
                    if (it.isLowerCase()) it.titlecase() else it.toString() 
                }

                // Progress text
                ProgressText.text = "${stat.earnedPoints} / ${stat.maxPoints}"

                // Percentage
                PercentageText.text = "${stat.percentage}%"
                
                // Màu sắc theo phần trăm
                val colorRes = when {
                    stat.percentage >= 80 -> android.R.color.holo_green_dark
                    stat.percentage >= 60 -> android.R.color.holo_orange_dark
                    else -> android.R.color.holo_red_dark
                }
                val color = ContextCompat.getColor(root.context, colorRes)
                PercentageText.setTextColor(color)

                // Progress bar
                ProgressBar.max = 100
                ProgressBar.setProgressCompat(stat.percentage, true)
                ProgressBar.setIndicatorColor(color)
                ProgressBar.trackColor = ContextCompat.getColor(root.context, android.R.color.darker_gray)
                
                // Icon emoji - Learning/Weak/Learned giống StudySets và Checklist
                val emoji = when {
                    stat.percentage >= 80 -> "✓" // Learned
                    stat.percentage >= 60 -> "📚" // Weak
                    else -> "●" // Learning
                }
                IconEmoji.text = emoji
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVocabStatBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class DiffCallback : DiffUtil.ItemCallback<VocabStatItem>() {
        override fun areItemsTheSame(oldItem: VocabStatItem, newItem: VocabStatItem): Boolean {
            return oldItem.vocab == newItem.vocab
        }

        override fun areContentsTheSame(oldItem: VocabStatItem, newItem: VocabStatItem): Boolean {
            return oldItem == newItem
        }
    }
}

