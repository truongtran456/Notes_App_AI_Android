package com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig.colorpicker

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.BR
import com.philkes.notallyx.R
import com.philkes.notallyx.common.extension.addSpaceDecoration
import com.philkes.notallyx.common.ui.view.colorview.ColorPickerItem
import com.philkes.notallyx.core.ui.widget.AbstractView
import com.philkes.notallyx.databinding.ItemDrawColorPaletteViewBinding

class ColorPaletteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractView(context, attrs) {

    interface OnItemClickListener {
        fun onClick(color: ColorPickerItem)
    }

    override fun layoutId(): Int = R.layout.item_draw_color_palette_view

    override fun viewBinding(): ItemDrawColorPaletteViewBinding =
        binding as ItemDrawColorPaletteViewBinding

    var listener: OnItemClickListener? = null

    var paletteColors: List<ColorPickerItem> = emptyList()
        set(value) {
            field = value
            if (isViewInitialized) {
                colorPaletteAdapter.updateListItems(value)
                viewBinding().setVariable(BR.paletteColors, value)
                viewBinding().executePendingBindings()
            }
        }

    var colorType: ColorType = ColorType.COLOR
        set(value) {
            field = value
            if (isViewInitialized) {
                setupLayoutManager()
            }
        }

    private val colorPaletteAdapter: ColorPaletteAdapter by lazy {
        ColorPaletteAdapter(
            context = context,
            listener =
                object : ColorPaletteAdapter.OnItemClickListener {
                    override fun onClick(color: ColorPickerItem) {
                        listener?.onClick(color)
                    }
                },
        )
    }

    override fun viewInitialized() {
        post {
            setupRecyclerView()
        }
    }

    private fun setupRecyclerView() {
        val spacing = resources.getDimension(R.dimen.dp_8).toInt()
        viewBinding().colorRecyclerView.apply {
            adapter = colorPaletteAdapter
            setupLayoutManager()
            addSpaceDecoration(space = spacing, includeEdge = false)
        }
        viewBinding().setVariable(BR.paletteColors, paletteColors)
        viewBinding().executePendingBindings()
    }

    private fun setupLayoutManager() {
        val spacing = resources.getDimension(R.dimen.dp_16).toInt()
        val numColumns =
            when (colorType) {
                ColorType.COLOR -> 8
                ColorType.COLOR_PALETTE -> 6
            }

        viewBinding().colorRecyclerView.layoutManager =
            object : GridLayoutManager(context, numColumns, RecyclerView.VERTICAL, false) {
                override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
                    val itemWidth = (width - (numColumns - 1) * spacing) / numColumns
                    lp.width = itemWidth
                    return true
                }
            }
    }
}

