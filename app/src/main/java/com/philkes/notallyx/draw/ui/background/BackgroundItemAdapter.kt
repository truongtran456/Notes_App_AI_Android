package com.philkes.notallyx.draw.ui.background

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.common.extension.debounceClick
import com.philkes.notallyx.databinding.ItemBackgroundItemBinding

class BackgroundItemAdapter(
    val items: MutableList<BackgroundItem>, // ✅ Đổi thành val để có thể access từ bên ngoài
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
    
    fun updateItems(newItems: MutableList<BackgroundItem>) {
        // ✅ Luôn update để đảm bảo sync state đúng
        val oldSize = items.size
        val newSize = newItems.size
        
        if (oldSize != newSize) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
            return
        }
        
        // ✅ Update tất cả items và tìm những item có thay đổi selection
        val changedPositions = mutableListOf<Int>()
        items.forEachIndexed { index, oldItem ->
            val newItem = newItems.getOrNull(index)
            if (newItem != null) {
                // ✅ Chỉ check isSelected vì đây là thay đổi chính cần notify
                val selectionChanged = oldItem.isSelected != newItem.isSelected
                
                if (selectionChanged) {
                    // Update item với state mới
                    items[index] = newItem.copy()
                    changedPositions.add(index)
                } else {
                    // Vẫn update item để đảm bảo sync (nhưng không notify)
                    items[index] = newItem.copy()
                }
            }
        }
        
        // ✅ Notify những item đã thay đổi selection
        if (changedPositions.isNotEmpty()) {
            changedPositions.forEach { position ->
                notifyItemChanged(position)
            }
        }
    }

    inner class BackgroundItemViewHolder(
        private val binding: ItemBackgroundItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BackgroundItem) {
            // ✅ Lưu item vào tag của root để có thể access lại khi click
            binding.root.tag = item

            // Load ảnh/màu chỉ khi cần thiết - lazy load
            val currentTag = binding.ivBackground.tag
            if (item.isCustomAdd) {
                // Ô "Custom +": dùng icon plus đơn giản, giữ nền để tint
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
                // ✅ Lazy load ảnh - chỉ load khi chưa load hoặc thay đổi
                // ✅ Defer load để tránh block main thread
                if (currentTag != item.drawableResId) {
                    // Clear ảnh cũ trước
                    binding.ivBackground.setImageDrawable(null)
                    binding.ivBackground.tag = item.drawableResId
                    binding.ivBackground.background = null
                    
                    // ✅ Post để load ảnh sau khi layout xong, giảm lag
                    binding.ivBackground.post {
                        // Double check để tránh load lại nếu đã thay đổi
                        if (binding.ivBackground.tag == item.drawableResId) {
                            try {
                                binding.ivBackground.setImageResource(item.drawableResId)
                            } catch (e: Exception) {
                                // Ignore nếu resource không tồn tại
                            }
                        }
                    }
                }
            } else {
                // Hiển thị màu solid
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

            // ✅ QUAN TRỌNG: Hiển thị/ẩn border dựa vào isSelected (theo pattern trong tài liệu)
            // Luôn update visibility để đảm bảo sync state đúng
            val isSelected = item.isSelected
            binding.clImageSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.clImage.isSelected = isSelected

            // ✅ Đảm bảo root view clickable và set click listener
            // Reset clickable state để đảm bảo luôn click được
            binding.root.isClickable = true
            binding.root.isFocusable = true
            
            // ✅ Remove old click listener trước khi set mới để tránh conflict
            binding.root.setOnClickListener(null)
            
            // ✅ Dùng debounceClick để tránh spam click và ANR
            // Lấy item từ tag để đảm bảo đúng item hiện tại
            binding.root.debounceClick {
                val currentItem = binding.root.tag as? BackgroundItem ?: item
                onItemClick(currentItem)
            }
        }
    }
}


