package com.starnest.common.ui.view.colorview

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.starnest.common.BR
import com.starnest.common.R
import com.starnest.common.databinding.ItemColorViewBinding
import com.starnest.core.extension.addSpaceDecoration
import com.starnest.core.ui.widget.AbstractView

class ColorPickerView(context: Context, attrs: AttributeSet) : AbstractView(context, attrs) {
    override fun layoutId(): Int = R.layout.item_color_view

    interface OnItemClickListener {
        fun onItemClick(color: ColorPickerItem)
    }

    var listener: OnItemClickListener? = null

    private val colorAdapter by lazy {
        ColorPickerAdapter(
            context = context,
            listener = object : ColorPickerAdapter.OnItemClickListener {
                override fun onItemClick(color: ColorPickerItem) {
                    listener?.onItemClick(color)
                }
            }
        )
    }

    override fun viewBinding(): ItemColorViewBinding = binding as ItemColorViewBinding

    var colors: List<ColorPickerItem> = arrayListOf()
        set(value) {
            field = value
            setupLayoutManager()
            viewBinding().setVariable(BR.colors, value)
            viewBinding().executePendingBindings()
        }




    override fun viewInitialized() {
        post {
            setupRecyclerView()
        }
    }

    private fun setupRecyclerView() {
        val spacing = resources.getDimension(com.starnest.core.R.dimen.dp_6).toInt()
        val numCol = colors.size

        if (numCol == 0) {
            return
        }

        viewBinding().apply {
            colorRecyclerView.apply {
                adapter = colorAdapter
                layoutManager =
                    object : GridLayoutManager(context, numCol, RecyclerView.VERTICAL, false) {
                        override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
                            val itemWidth = (width - (numCol - 1) * spacing) / numCol
                            lp.width = itemWidth
                            return true
                        }
                    }
                addSpaceDecoration(space = spacing, false)
            }
        }

        viewBinding().setVariable(BR.colors, colors)
        viewBinding().executePendingBindings()
    }

    private fun setupLayoutManager() {
        if (colors.isEmpty()) return

        val spacing = resources.getDimension(com.starnest.core.R.dimen.dp_6).toInt()

        val numCol = colors.size

        viewBinding().apply {
            colorRecyclerView.apply {
                layoutManager =
                    object : GridLayoutManager(context, numCol, RecyclerView.VERTICAL, false) {
                        override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
                            val itemWidth = (width - (numCol - 1) * spacing) / numCol
                            lp.width = itemWidth
                            return true
                        }
                    }
            }
        }
    }
}