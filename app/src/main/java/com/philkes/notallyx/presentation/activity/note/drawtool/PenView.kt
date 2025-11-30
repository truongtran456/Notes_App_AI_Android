package com.philkes.notallyx.presentation.activity.note.drawtool

import android.content.Context
import android.util.AttributeSet
import com.philkes.notallyx.R
import com.philkes.notallyx.common.model.DrawToolBrush
import com.philkes.notallyx.core.ui.widget.AbstractView
import com.philkes.notallyx.draw.ui.newdraw.view.drawtool.DrawToolData
import com.philkes.notallyx.databinding.ItemPenViewBinding

class PenView(
    context: Context,
    attrs: AttributeSet?
) : AbstractView(context, attrs) {

    var drawToolBrush: DrawToolBrush? = null
        set(value) {
            field = value
            if (isViewInitialized && value != null) {
                val iconResId = if (drawToolBrush?.isAdd ?: false) {
                    R.drawable.ic_pen_crayon_edit_body
                } else {
                    DrawToolData.getBrushResId(value.brush)
                }
                viewBinding().ivPen.setImageResource(iconResId)
                viewBinding().ivShadow.setImageResource(iconResId)
            }
        }

    override fun layoutId(): Int = R.layout.item_pen_view

    override fun viewBinding() = binding as ItemPenViewBinding

    override fun viewInitialized() {
        // View initialized
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        viewBinding().ivPen.isSelected = selected
        viewBinding().ivShadow.isSelected = selected
    }
}

