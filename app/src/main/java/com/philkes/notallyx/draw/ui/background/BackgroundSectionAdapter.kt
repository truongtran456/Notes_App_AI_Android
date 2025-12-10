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

    // Map ?? l?u adapter reference theo section type
    private val adapterMap = mutableMapOf<BackgroundCategoryType, BackgroundItemAdapter>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding =
            ItemBackgroundSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SectionViewHolder(binding)
    }

    override fun getItemCount(): Int = sections.size

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        holder.bind(sections[position])
    }

    fun updateSections(newSections: List<BackgroundSection>) {
        // ? Luôn update ?? ??m b?o sync state
        sections.clear()
        sections.addAll(newSections)

        // ? Update t?t c? adapter con v?i items m?i (QUAN TR?NG!)
        newSections.forEach { section ->
            adapterMap[section.type]?.updateItems(section.items.toMutableList())
        }

        // ? Notify ?? rebind các section
        notifyDataSetChanged()
    }

    inner class SectionViewHolder(private val binding: ItemBackgroundSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(section: BackgroundSection) {
            binding.tvTitle.text = section.title

            val context = binding.root.context
            val spacing = context.resources.getDimension(com.philkes.notallyx.R.dimen.dp_12).toInt()
            val spanCount = 3.8f // Hi?n th? ~3-4 items/hàng

            // ? QUAN TR?NG: L?y ho?c t?o adapter t? map
            val itemAdapter =
                adapterMap.getOrPut(section.type) {
                    BackgroundItemAdapter(section.items.toMutableList(), onItemClick)
                }

            // ? QUAN TR?NG: Luôn update adapter v?i section.items m?i m?i l?n bind
            // ?i?u này ??m b?o state ???c sync ?úng (theo pattern trong tài li?u)
            itemAdapter.updateItems(section.items.toMutableList())

            binding.rvItems.apply {
                // Ch? set adapter và layout manager l?n ??u
                if (adapter == null) {
                    setHasFixedSize(true) // T?i ?u performance
                    setItemViewCacheSize(3) // Gi?m cache ?? gi?m memory (t? 5 xu?ng 3)
                    isDrawingCacheEnabled = false // T?t drawing cache ?? gi?m memory
                    setRecycledViewPool(
                        RecyclerView.RecycledViewPool().apply {
                            setMaxRecycledViews(0, 5) // Gi?i h?n s? l??ng view ???c recycle
                        }
                    )

                    adapter = itemAdapter

                    // Setup layout manager v?i tính toán item width - cache k?t qu?
                    var cachedItemWidth = 0
                    post {
                        layoutManager =
                            object :
                                LinearLayoutManager(
                                    context,
                                    LinearLayoutManager.HORIZONTAL,
                                    false,
                                ) {
                                override fun checkLayoutParams(
                                    lp: RecyclerView.LayoutParams
                                ): Boolean {
                                    if (cachedItemWidth == 0) {
                                        val parentWidth = width
                                        if (parentWidth > 0) {
                                            // Tính width m?i item: (parentWidth - spacing) /
                                            // spanCount
                                            cachedItemWidth =
                                                ((parentWidth - (spanCount - 1) * spacing) /
                                                        spanCount)
                                                    .toInt()
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
