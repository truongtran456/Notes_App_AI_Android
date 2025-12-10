package com.philkes.notallyx.draw.ui.background

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.common.extension.debounceClick
import com.philkes.notallyx.databinding.ItemBackgroundItemBinding

class BackgroundItemAdapter(
    val items: MutableList<BackgroundItem>, // ? ??i thành val ?? có th? access t? bên ngoài
    private val onItemClick: (BackgroundItem) -> Unit,
) : RecyclerView.Adapter<BackgroundItemAdapter.BackgroundItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackgroundItemViewHolder {
        val binding =
            ItemBackgroundItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BackgroundItemViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: BackgroundItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun updateItems(newItems: MutableList<BackgroundItem>) {
        // ? Luôn update ?? ??m b?o sync state ?úng
        val oldSize = items.size
        val newSize = newItems.size

        if (oldSize != newSize) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
            return
        }

        // ? Update t?t c? items và tìm nh?ng item có thay ??i selection
        val changedPositions = mutableListOf<Int>()
        items.forEachIndexed { index, oldItem ->
            val newItem = newItems.getOrNull(index)
            if (newItem != null) {
                // ? Ch? check isSelected vì ?ây là thay ??i chính c?n notify
                val selectionChanged = oldItem.isSelected != newItem.isSelected

                if (selectionChanged) {
                    // Update item v?i state m?i
                    items[index] = newItem.copy()
                    changedPositions.add(index)
                } else {
                    // V?n update item ?? ??m b?o sync (nh?ng không notify)
                    items[index] = newItem.copy()
                }
            }
        }

        // ? Notify nh?ng item ?ã thay ??i selection
        if (changedPositions.isNotEmpty()) {
            changedPositions.forEach { position -> notifyItemChanged(position) }
        }
    }

    inner class BackgroundItemViewHolder(private val binding: ItemBackgroundItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BackgroundItem) {
            // ? L?u item vào tag c?a root ?? có th? access l?i khi click
            binding.root.tag = item

            // Load ?nh/màu ch? khi c?n thi?t - lazy load
            val currentTag = binding.ivBackground.tag
            if (item.isCustomAdd) {
                // Ô "Custom +": dùng icon plus ??n gi?n, gi? n?n ?? tint
                if (currentTag != "custom_add") {
                    binding.ivBackground.setImageResource(android.R.drawable.ic_input_add)
                    binding.ivBackground.tag = "custom_add"
                }
                val baseDrawable =
                    binding.ivBackground.background?.let { DrawableCompat.wrap(it.mutate()) }
                if (baseDrawable != null) {
                    DrawableCompat.setTint(baseDrawable, item.colorInt)
                    binding.ivBackground.background = baseDrawable
                }
            } else if (item.drawableResId != null) {
                // ? Lazy load ?nh - ch? load khi ch?a load ho?c thay ??i
                // ? Defer load ?? tránh block main thread
                if (currentTag != item.drawableResId) {
                    // Clear ?nh c? tr??c
                    binding.ivBackground.setImageDrawable(null)
                    binding.ivBackground.tag = item.drawableResId
                    binding.ivBackground.background = null

                    // ? Post ?? load ?nh sau khi layout xong, gi?m lag
                    binding.ivBackground.post {
                        // Double check ?? tránh load l?i n?u ?ã thay ??i
                        if (binding.ivBackground.tag == item.drawableResId) {
                            try {
                                binding.ivBackground.setImageResource(item.drawableResId)
                            } catch (e: Exception) {
                                // Ignore n?u resource không t?n t?i
                            }
                        }
                    }
                }
            } else {
                // Hi?n th? màu solid
                val colorTag = "color_${item.colorInt}"
                if (currentTag != colorTag) {
                    binding.ivBackground.setImageDrawable(null)
                    binding.ivBackground.tag = colorTag
                    val baseDrawable =
                        binding.ivBackground.background?.let { DrawableCompat.wrap(it.mutate()) }
                    if (baseDrawable != null) {
                        DrawableCompat.setTint(baseDrawable, item.colorInt)
                        binding.ivBackground.background = baseDrawable
                    }
                }
            }

            // ? QUAN TR?NG: Hi?n th?/?n border d?a vào isSelected (theo pattern trong tài li?u)
            // Luôn update visibility ?? ??m b?o sync state ?úng
            val isSelected = item.isSelected
            binding.clImageSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.clImage.isSelected = isSelected

            // ? ??m b?o root view clickable và set click listener
            // Reset clickable state ?? ??m b?o luôn click ???c
            binding.root.isClickable = true
            binding.root.isFocusable = true

            // ? Remove old click listener tr??c khi set m?i ?? tránh conflict
            binding.root.setOnClickListener(null)

            // ? Dùng debounceClick ?? tránh spam click và ANR
            // L?y item t? tag ?? ??m b?o ?úng item hi?n t?i
            binding.root.debounceClick {
                val currentItem = binding.root.tag as? BackgroundItem ?: item
                onItemClick(currentItem)
            }
        }
    }
}
