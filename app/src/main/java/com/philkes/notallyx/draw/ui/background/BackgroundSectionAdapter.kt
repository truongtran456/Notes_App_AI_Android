package com.philkes.notallyx.draw.ui.background

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.common.extension.addSpaceDecoration
import com.philkes.notallyx.databinding.ItemBackgroundSectionBinding

class BackgroundSectionAdapter(
    private val sections: MutableList<BackgroundSection>,
    private val onItemClick: (BackgroundItem) -> Unit,
) : RecyclerView.Adapter<BackgroundSectionAdapter.SectionViewHolder>() {
    
    // Map để lưu adapter reference theo section type
    private val adapterMap = mutableMapOf<BackgroundCategoryType, BackgroundItemAdapter>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): SectionViewHolder {
        val binding =
            ItemBackgroundSectionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return SectionViewHolder(binding)
    }

    override fun getItemCount(): Int = sections.size

    override fun onBindViewHolder(
        holder: SectionViewHolder,
        position: Int,
    ) {
        holder.bind(sections[position])
    }

    fun updateSections(newSections: List<BackgroundSection>) {
        // ✅ Luôn update để đảm bảo sync state
        sections.clear()
        sections.addAll(newSections)
        
        // ✅ Update tất cả adapter con với items mới (QUAN TRỌNG!)
        newSections.forEach { section ->
            adapterMap[section.type]?.updateItems(section.items.toMutableList())
        }
        
        // ✅ Notify để rebind các section
        notifyDataSetChanged()
    }

    inner class SectionViewHolder(
        private val binding: ItemBackgroundSectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(section: BackgroundSection) {
            binding.tvTitle.text = section.title

            val context = binding.root.context
            val spacing = context.resources.getDimension(com.philkes.notallyx.R.dimen.dp_12).toInt()
            val spanCount = 3.8f // Hiển thị ~3-4 items/hàng

            // ✅ QUAN TRỌNG: Lấy hoặc tạo adapter từ map
            val itemAdapter = adapterMap.getOrPut(section.type) {
                BackgroundItemAdapter(section.items.toMutableList(), onItemClick)
            }
            
            // ✅ QUAN TRỌNG: Luôn update adapter với section.items mới mỗi lần bind
            // Điều này đảm bảo state được sync đúng (theo pattern trong tài liệu)
            itemAdapter.updateItems(section.items.toMutableList())

            binding.rvItems.apply {
                // Chỉ set adapter và layout manager lần đầu
                if (adapter == null) {
                    setHasFixedSize(true) // Tối ưu performance
                    setItemViewCacheSize(3) // Giảm cache để giảm memory (từ 5 xuống 3)
                    isDrawingCacheEnabled = false // Tắt drawing cache để giảm memory
                    setRecycledViewPool(RecyclerView.RecycledViewPool().apply {
                        setMaxRecycledViews(0, 5) // Giới hạn số lượng view được recycle
                    })
                    
                    adapter = itemAdapter
                    
                    // Setup layout manager với tính toán item width - cache kết quả
                    var cachedItemWidth = 0
                    post {
                        layoutManager = object : LinearLayoutManager(
                        context,
                            LinearLayoutManager.HORIZONTAL,
                        false,
                        ) {
                            override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
                                if (cachedItemWidth == 0) {
                                    val parentWidth = width
                                    if (parentWidth > 0) {
                                        // Tính width mỗi item: (parentWidth - spacing) / spanCount
                                        cachedItemWidth = ((parentWidth - (spanCount - 1) * spacing) / spanCount).toInt()
                                    }
                                }
                                if (cachedItemWidth > 0) {
                                    lp.width = cachedItemWidth
                                }
                                return true
                            }
                        }
                    }
                    
                if (itemDecorationCount == 0) {
                    addSpaceDecoration(spacing, includeEdge = false)
                    }
                }
            }
        }
    }
}


