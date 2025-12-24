package com.philkes.notallyx.presentation.view.study

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ItemOverviewCardBinding

data class OverviewCard(
    val iconResId: Int,
    val value: String,
    val label: String
)

class OverviewCardAdapter : ListAdapter<OverviewCard, OverviewCardAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(
        private val binding: ItemOverviewCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card: OverviewCard, position: Int) {
            binding.apply {
                // Set gradient background động cho overview card
                val cardContent = root.findViewById<android.view.ViewGroup>(R.id.CardContent)
                if (cardContent != null) {
                    val gradients = listOf(
                        R.drawable.bg_overview_card_gradient_purple, // Purple pastel
                        R.drawable.bg_overview_card_gradient_green,   // Green pastel
                        R.drawable.bg_overview_card_gradient_orange  // Orange pastel
                    )
                    val gradientIndex = position % gradients.size
                    cardContent.setBackgroundResource(gradients[gradientIndex])
                    
                    // Set icon tint màu đậm hơn để nổi bật trên nền pastel
                    val iconTints = listOf(
                        "#9C27B0", // Purple đậm
                        "#4CAF50", // Green đậm
                        "#FF9800"  // Orange đậm
                    )
                    Icon.setColorFilter(android.graphics.Color.parseColor(iconTints[gradientIndex]))
                }
                
                Icon.setImageResource(card.iconResId)
                
                // Format: "12 Sets", "320 Words", "5-day Streak"
                // Value sẽ chứa cả số và đơn vị (ví dụ: "12 Sets")
                // Label sẽ chỉ là phần mô tả ngắn (ví dụ: "Learned")
                if (card.value.contains(" ")) {
                    // Nếu value đã có format "12 Sets"
                    Value.text = card.value
                    Label.text = card.label
                } else {
                    // Nếu value chỉ là số, format lại
                    Value.text = card.value
                    Label.text = card.label
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOverviewCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        
        // Tính toán width để hiển thị vừa 3 card
        val screenWidth = parent.context.resources.displayMetrics.widthPixels
        val paddingHorizontal = (16 * 2 * parent.context.resources.displayMetrics.density).toInt() // 16dp * 2
        val margins = (8 * 2 * parent.context.resources.displayMetrics.density).toInt() // 8dp margin giữa các card
        val cardWidth = (screenWidth - paddingHorizontal - margins) / 3
        
        val layoutParams = binding.root.layoutParams
        layoutParams.width = cardWidth
        binding.root.layoutParams = layoutParams
        
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    private class DiffCallback : DiffUtil.ItemCallback<OverviewCard>() {
        override fun areItemsTheSame(oldItem: OverviewCard, newItem: OverviewCard): Boolean {
            return oldItem.label == newItem.label
        }

        override fun areContentsTheSame(oldItem: OverviewCard, newItem: OverviewCard): Boolean {
            return oldItem == newItem
        }
    }
}

