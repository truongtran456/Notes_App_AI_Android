package com.philkes.notallyx.draw.ui.background

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.common.extension.addSpaceDecoration
import com.philkes.notallyx.databinding.ItemCustomColorPageLayoutBinding

class CustomColorPageAdapter(
    private val context: Context,
    private var pages: List<List<ColorCustomItem>>,
    private val listener: OnItemClickListener,
) : RecyclerView.Adapter<CustomColorPageAdapter.PageViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(colorIndex: Int)
        fun onAddClick(colorString: String)
        fun onDeleteClick(colorIndex: Int)
        fun onUpdateColorClick(oldColor: ColorCustomItem, newColorString: String)
    }

    fun updatePages(newPages: List<List<ColorCustomItem>>) {
        pages = newPages
        notifyDataSetChanged()
    }

    private var editMode: Boolean = false

    fun updateEditMode(editMode: Boolean) {
        this.editMode = editMode
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding =
            ItemCustomColorPageLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return PageViewHolder(binding, editMode, listener)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.updateEditMode(editMode)
        holder.bind(pages.getOrNull(position) ?: emptyList(), position)
    }

    override fun getItemCount(): Int = pages.size

    inner class PageViewHolder(
        private val binding: ItemCustomColorPageLayoutBinding,
        private var editMode: Boolean,
        private val listener: OnItemClickListener,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val colorAdapter: CustomColorAdapter by lazy {
            CustomColorAdapter(
                context,
                object : CustomColorAdapter.OnItemClickListener {
                    override fun onItemClick(colorIndex: Int) {
                        // Map index trong page -> index thật trong toàn bộ list màu
                        val currentPage = bindingAdapterPosition
                        val pagesAdapter =
                            (binding.root.parent as? RecyclerView)?.adapter
                                as? CustomColorPageAdapter
                        val offset =
                            pagesAdapter?.pages
                                ?.take(currentPage)
                                ?.sumOf { it.size } ?: 0
                        val realIndex = offset + colorIndex
                        listener.onItemClick(realIndex)
                    }

                    override fun onMoreClick() {
                        // no-op (được handle bên trong CustomColorAdapter)
                    }

                    override fun onAddClick(colorString: String) {
                        listener.onAddClick(colorString)
                    }

                    override fun onDeleteClick(colorIndex: Int) {
                        listener.onDeleteClick(colorIndex)
                    }

                    override fun onUpdateColorClick(
                        oldColor: ColorCustomItem,
                        newColorString: String,
                    ) {
                        listener.onUpdateColorClick(oldColor, newColorString)
                    }
                },
            )
        }

        fun bind(colors: List<ColorCustomItem>, pageIndex: Int) {
            colorAdapter.isEditMode = editMode
            colorAdapter.updateListItems(ArrayList(colors))
            binding.rvColors.adapter = colorAdapter
        }

        fun updateEditMode(editMode: Boolean) {
            this.editMode = editMode
            colorAdapter.isEditMode = editMode
        }

        init {
            val spacing = context.resources.getDimension(com.philkes.notallyx.R.dimen.dp_6).toInt()
            val numCol = 6 // 2 hàng x 6 màu
            binding.rvColors.apply {
                adapter = colorAdapter
                layoutManager =
                    object :
                        GridLayoutManager(
                            context,
                            numCol,
                            RecyclerView.VERTICAL,
                            false,
                        ) {
                        override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
                            val itemWidth = (width - (numCol - 1) * spacing) / numCol
                            lp.width = itemWidth
                            lp.height = itemWidth
                            return true
                        }
                    }
                addSpaceDecoration(space = spacing, includeEdge = false)
            }
        }
    }
}


