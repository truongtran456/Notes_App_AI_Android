package com.starnest.common.ui.view.headerview

import android.content.Context
import android.util.AttributeSet
import com.starnest.common.R
import com.starnest.common.databinding.ItemHeaderViewBinding
import com.starnest.core.extension.debounceClick
import com.starnest.core.extension.gone
import com.starnest.core.ui.widget.AbstractView

class HeaderView(context: Context, attrs: AttributeSet) : AbstractView(context, attrs) {
    override fun layoutId(): Int = R.layout.item_header_view

    interface OnHeaderViewClickListener {
        fun onSeeAll()
    }

    var onHeaderViewClickListener: OnHeaderViewClickListener? = null

    override fun viewBinding(): ItemHeaderViewBinding = binding as ItemHeaderViewBinding

    var header: Header? = null
        set(value) {
            field = value

            if (isViewInitialized) {
                setupUI()
            }
        }


    override fun viewInitialized() {
        post { setupUI() }
    }

    private fun setupUI() {
        val header = header ?: return

        viewBinding().apply {
            seeAllView.root.gone(!header.isSeeAllEnabled)
            ivIcon.setImageResource(header.iconResId)
            tvTitle.text = header.title

            if (header.titleColor != 0) {
                tvTitle.setTextColor(header.titleColor)
            }

            seeAllView.root.debounceClick{
                onHeaderViewClickListener?.onSeeAll()
            }
        }
    }
}