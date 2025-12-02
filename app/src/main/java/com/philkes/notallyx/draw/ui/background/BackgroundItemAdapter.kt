package com.philkes.notallyx.draw.ui.background

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.ItemBackgroundItemBinding

class BackgroundItemAdapter(
    private val items: MutableList<BackgroundItem>,
    private val onItemClick: (BackgroundItem) -> Unit,
) : RecyclerView.Adapter<BackgroundItemAdapter.BackgroundItemViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): BackgroundItemViewHolder {
        val binding =
            ItemBackgroundItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return BackgroundItemViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(
        holder: BackgroundItemViewHolder,
        position: Int,
    ) {
        holder.bind(items[position])
    }

    inner class BackgroundItemViewHolder(
        private val binding: ItemBackgroundItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BackgroundItem) {
            val baseDrawable =
                binding.ivBackground.background?.let { DrawableCompat.wrap(it.mutate()) }

            if (item.isCustomAdd) {
                // Ô "Custom +": dùng icon plus đơn giản, giữ nền để tint
                binding.ivBackground.setImageResource(android.R.drawable.ic_input_add)
            } else {
                binding.ivBackground.setImageDrawable(null)
            }

            if (baseDrawable != null) {
                DrawableCompat.setTint(baseDrawable, item.colorInt)
                binding.ivBackground.background = baseDrawable
            }

            binding.selectionOverlay.visibility =
                if (item.isSelected) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}


