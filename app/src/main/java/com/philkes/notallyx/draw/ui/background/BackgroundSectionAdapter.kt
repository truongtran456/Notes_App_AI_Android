package com.philkes.notallyx.draw.ui.background

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.common.extension.addSpaceDecoration
import com.philkes.notallyx.databinding.ItemBackgroundSectionBinding

class BackgroundSectionAdapter(
    private val sections: MutableList<BackgroundSection>,
    private val onItemClick: (BackgroundItem) -> Unit,
) : RecyclerView.Adapter<BackgroundSectionAdapter.SectionViewHolder>() {

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
        sections.clear()
        sections.addAll(newSections)
        notifyDataSetChanged()
    }

    inner class SectionViewHolder(
        private val binding: ItemBackgroundSectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(section: BackgroundSection) {
            binding.tvTitle.text = section.title

            val context = binding.root.context
            val spacing = context.resources.getDimension(com.philkes.notallyx.R.dimen.dp_8).toInt()

            val adapter =
                BackgroundItemAdapter(section.items, onItemClick)

            binding.rvItems.apply {
                this.adapter = adapter
                layoutManager =
                    GridLayoutManager(
                        context,
                        4,
                        RecyclerView.VERTICAL,
                        false,
                    )
                if (itemDecorationCount == 0) {
                    addSpaceDecoration(spacing, includeEdge = false)
                }
            }
        }
    }
}


