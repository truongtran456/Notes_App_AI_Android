package com.philkes.notallyx.draw.ui.background

import android.content.Context
import android.util.AttributeSet
import androidx.viewpager2.widget.ViewPager2
import com.philkes.notallyx.core.ui.widget.AbstractView
import com.philkes.notallyx.databinding.ItemColorCustomLayoutBinding
import com.philkes.notallyx.R
import com.philkes.notallyx.common.extension.gone
import com.philkes.notallyx.common.extension.show

class CustomColorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractView(context, attrs) {

    override fun layoutId(): Int = R.layout.item_color_custom_layout

    override fun viewBinding(): ItemColorCustomLayoutBinding =
        binding as ItemColorCustomLayoutBinding

    interface OnItemClickListener {
        fun onItemClick(colorIndex: Int)
        fun onAddClick(colorString: String)
        fun onMoreClick()
        fun onDeleteClick(colorIndex: Int)
        fun onUpdateColorClick(oldColor: ColorCustomItem, newColorString: String)
    }

    var listener: OnItemClickListener? = null

    private var isEditMode = false

    private val pageAdapter: CustomColorPageAdapter by lazy {
        CustomColorPageAdapter(
            context = context,
            pages = emptyList(),
            listener =
                object : CustomColorPageAdapter.OnItemClickListener {
                    override fun onItemClick(colorIndex: Int) {
                        listener?.onItemClick(colorIndex)
                    }

                    override fun onAddClick(colorString: String) {
                        listener?.onAddClick(colorString)
                    }

                    override fun onDeleteClick(colorIndex: Int) {
                        listener?.onDeleteClick(colorIndex)
                    }

                    override fun onUpdateColorClick(
                        oldColor: ColorCustomItem,
                        newColorString: String,
                    ) {
                        listener?.onUpdateColorClick(oldColor, newColorString)
                    }
                },
        )
    }

    var colors: List<ColorCustomItem> = arrayListOf()
        set(value) {
            val oldPages = divideIntoPages(field, ITEMS_PER_PAGE).size
            field = value
            val pages = divideIntoPages(value, ITEMS_PER_PAGE)
            val newPages = pages.size
            pageAdapter.updatePages(pages)
            if (newPages > oldPages) {
                viewBinding().viewPager.setCurrentItem(newPages - 1, true)
                viewBinding().indicator.animatePageSelected(newPages - 1)
            }
        }

    override fun viewInitialized() {
        post {
            setupViewPager()
            setupAction()
        }
    }

    private fun setupAction() = viewBinding().apply {
        ivEditCustom.setOnClickListener { toggleEditMode(true) }
        tvCancel.setOnClickListener { toggleEditMode(false) }
    }

    private fun toggleEditMode(enable: Boolean) {
        isEditMode = enable
        viewBinding().apply {
            if (enable) {
                ivEditCustom.gone()
                tvCancel.show()
            } else {
                ivEditCustom.show()
                tvCancel.gone()
            }
        }
        pageAdapter.updateEditMode(enable)
    }

    private fun setupViewPager() = viewBinding().apply {
        viewPager.adapter = pageAdapter
        viewPager.offscreenPageLimit = 1
        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    indicator.animatePageSelected(position)
                }
            },
        )
        indicator.setViewPager(viewPager)
    }

    private fun divideIntoPages(
        items: List<ColorCustomItem>,
        itemsPerPage: Int,
    ): List<List<ColorCustomItem>> {
        if (items.isEmpty()) return emptyList()
        return items.chunked(itemsPerPage)
    }

    companion object {
        // 2 hàng x 6 màu
        private const val ITEMS_PER_PAGE = 12
    }
}


