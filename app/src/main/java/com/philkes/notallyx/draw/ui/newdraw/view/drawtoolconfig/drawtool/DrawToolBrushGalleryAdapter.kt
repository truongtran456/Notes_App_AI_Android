package com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig.drawtool

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.philkes.notallyx.BR
import com.philkes.notallyx.common.extension.debounceClick
import com.philkes.notallyx.common.extension.px
import com.philkes.notallyx.common.extension.reloadChangedItems
import com.philkes.notallyx.common.model.DrawToolBrush
import com.philkes.notallyx.core.ui.adapter.TMVVMAdapter
import com.philkes.notallyx.core.ui.adapter.TMVVMViewHolder
import com.philkes.notallyx.databinding.ItemDrawToolBrushGalleryLayoutBinding
import com.philkes.notallyx.draw.ui.newdraw.view.drawtool.getBrushDescription
import com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig.preview.DrawToolPreviewView

class DrawToolBrushGalleryAdapter(
    private val context: Context,
    private val listener: OnItemClickListener,
) : TMVVMAdapter<DrawToolBrush>(arrayListOf()) {

    interface OnItemClickListener {
        fun onClick(drawBrush: DrawToolBrush)
    }

    override fun onCreateViewHolderBase(
        parent: ViewGroup?,
        viewType: Int,
    ): TMVVMViewHolder {
        val binding =
            ItemDrawToolBrushGalleryLayoutBinding.inflate(
                LayoutInflater.from(context),
                parent,
                false,
            )
        return TMVVMViewHolder(binding)
    }

    override fun onBindViewHolderBase(
        holder: TMVVMViewHolder?,
        position: Int,
    ) {
        val drawBrush = list.getOrNull(position) ?: return
        val binding = holder?.binding as ItemDrawToolBrushGalleryLayoutBinding

        binding.apply {
            root.debounceClick {
                reloadChangedItems { it.id == drawBrush.id }
                listener.onClick(drawBrush)
            }
            tvName.text = drawBrush.getBrushDescription(context)
            setupBrush(drawBrush, drawToolPreview)
            setVariable(BR.brush, drawBrush)
            executePendingBindings()
        }
    }

    private fun setupBrush(
        drawBrush: DrawToolBrush,
        drawToolPreview: DrawToolPreviewView,
    ) {
        drawToolPreview.post {
            drawToolPreview.apply {
                padding = 6.px
                mStrokeWidth = drawBrush.sliderSize.coerceAtLeast(4f)
                brush = drawBrush.brush
                mColor = drawBrush.color.toColorIntOrDefault()
                invalidate()
            }
        }
    }

    private fun String.toColorIntOrDefault(): Int =
        try {
            android.graphics.Color.parseColor(this)
        } catch (e: IllegalArgumentException) {
            android.graphics.Color.BLACK
        }

    override fun getDiffCallback(
        oldData: List<DrawToolBrush>,
        newData: List<DrawToolBrush>,
    ): DiffUtil.Callback =
        object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldData.size

            override fun getNewListSize(): Int = newData.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldData.getOrNull(oldItemPosition)
                val newItem = newData.getOrNull(newItemPosition)
                return oldItem?.id == newItem?.id && oldItem?.isSelected == newItem?.isSelected
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldData.getOrNull(oldItemPosition) == newData.getOrNull(newItemPosition)
        }
}

