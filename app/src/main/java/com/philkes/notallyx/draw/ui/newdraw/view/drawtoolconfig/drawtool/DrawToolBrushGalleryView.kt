package com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig.drawtool

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.BR
import com.philkes.notallyx.R
import com.philkes.notallyx.common.extension.addSpaceDecoration
import com.philkes.notallyx.common.model.DrawToolBrush
import com.philkes.notallyx.core.ui.widget.AbstractView
import com.philkes.notallyx.databinding.ItemDrawToolBrushGalleryViewBinding

class DrawToolBrushGalleryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractView(context, attrs) {

    interface OnItemClickListener {
        fun onClick(brush: DrawToolBrush)
    }

    override fun layoutId(): Int = R.layout.item_draw_tool_brush_gallery_view

    override fun viewBinding(): ItemDrawToolBrushGalleryViewBinding =
        binding as ItemDrawToolBrushGalleryViewBinding

    var listener: OnItemClickListener? = null

    var brushes: List<DrawToolBrush>? = emptyList()
        set(value) {
            field = value
            if (isViewInitialized && value != null) {
                brushAdapter.updateListItems(value)
                viewBinding().setVariable(BR.brushes, value)
                viewBinding().executePendingBindings()
            }
        }

    private val brushAdapter: DrawToolBrushGalleryAdapter by lazy {
        DrawToolBrushGalleryAdapter(
            context = context,
            listener =
                object : DrawToolBrushGalleryAdapter.OnItemClickListener {
                    override fun onClick(drawBrush: DrawToolBrush) {
                        listener?.onClick(drawBrush)
                    }
                },
        )
    }

    override fun viewInitialized() {
        post { setupRecyclerBrushView() }
    }

    private fun setupRecyclerBrushView() {
        viewBinding().brushRecyclerView.apply {
            adapter = brushAdapter
            setupLayoutManager()
        }
        brushes?.let { brushAdapter.updateListItems(it) }
    }

    private fun setupLayoutManager() {
        val spacing = resources.getDimension(R.dimen.dp_6).toInt()
        val spanCount = 4.5f
        viewBinding().brushRecyclerView.apply {
            layoutManager =
                object : GridLayoutManager(context, 2, RecyclerView.HORIZONTAL, false) {
                    override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
                        val itemWidth = (width - (spanCount - 1) * spacing) / spanCount
                        lp.width = itemWidth.toInt()
                        return true
                    }
                }
            addSpaceDecoration(spacing, includeEdge = true)
        }
    }
}

