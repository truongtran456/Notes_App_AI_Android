package com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig.colorpicker

import android.content.Context
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import com.philkes.notallyx.BR
import com.philkes.notallyx.R
import com.philkes.notallyx.common.extension.color
import com.philkes.notallyx.common.extension.debounceClick
import com.philkes.notallyx.common.extension.gone
import com.philkes.notallyx.common.extension.rawColor
import com.philkes.notallyx.common.extension.reloadChangedItems
import com.philkes.notallyx.common.extension.showMoreColor
import com.philkes.notallyx.common.ui.view.colorview.ColorPickerItem
import com.philkes.notallyx.core.ui.adapter.TMVVMAdapter
import com.philkes.notallyx.core.ui.adapter.TMVVMViewHolder
import com.philkes.notallyx.databinding.ItemDrawColorPaletteItemLayoutBinding
import com.philkes.notallyx.databinding.ItemDrawColorPaletteItemMoreLayoutBinding

class ColorPaletteAdapter(
    private val context: Context,
    private val listener: OnItemClickListener,
) : TMVVMAdapter<ColorPickerItem>(arrayListOf()) {

    interface OnItemClickListener {
        fun onClick(color: ColorPickerItem)
    }

    private object ViewType {
        const val COLOR = 0
        const val COLOR_MORE = 1
    }

    override fun getItemViewType(position: Int): Int {
        val color = list.getOrNull(position) ?: return ViewType.COLOR
        return if (color.isMore) {
            ViewType.COLOR_MORE
        } else {
            ViewType.COLOR
        }
    }

    override fun onCreateViewHolderBase(
        parent: ViewGroup?,
        viewType: Int,
    ): TMVVMViewHolder =
        when (viewType) {
            ViewType.COLOR_MORE -> {
                val binding =
                    ItemDrawColorPaletteItemMoreLayoutBinding.inflate(
                        LayoutInflater.from(parent?.context),
                        parent,
                        false,
                    )
                TMVVMViewHolder(binding)
            }

            else -> {
                val binding =
                    ItemDrawColorPaletteItemLayoutBinding.inflate(
                        LayoutInflater.from(parent?.context),
                        parent,
                        false,
                    )
                TMVVMViewHolder(binding)
            }
        }

    override fun onBindViewHolderBase(
        holder: TMVVMViewHolder?,
        position: Int,
    ) {
        when (getItemViewType(position)) {
            ViewType.COLOR_MORE -> configColorMoreLayout(holder, position)
            else -> configColorLayout(holder, position)
        }
    }

    private fun configColorMoreLayout(
        holder: TMVVMViewHolder?,
        position: Int,
    ) {
        val color = list.getOrNull(position) ?: return
        val binding = holder?.binding as? ItemDrawColorPaletteItemMoreLayoutBinding ?: return

        binding.clImageSelected.gone(!color.isSelected)
        binding.root.debounceClick {
            context.showMoreColor { colorInt ->
                val updated = color.apply { colorString = colorInt.rawColor() }
                list[position] = updated
                reloadChangedItems { it.colorString == updated.colorString }
                listener.onClick(updated)
            }
        }
    }

    private fun configColorLayout(
        holder: TMVVMViewHolder?,
        position: Int,
    ) {
        val color = list.getOrNull(position) ?: return
        val binding = holder?.binding as? ItemDrawColorPaletteItemLayoutBinding ?: return

        val background =
            ContextCompat.getDrawable(context, R.drawable.bg_item_color_normal) as? LayerDrawable
                ?: return
        background.findDrawableByLayerId(R.id.background).setTint(color.colorString.color)

        binding.clImageSelected.gone(!color.isSelected)
        binding.clImage.background = background
        binding.root.debounceClick {
            reloadChangedItems { it.colorString == color.colorString }
            listener.onClick(color)
        }
        binding.setVariable(BR.color, color)
        binding.executePendingBindings()
    }

    override fun getDiffCallback(
        oldData: List<ColorPickerItem>,
        newData: List<ColorPickerItem>,
    ): DiffUtil.Callback =
        object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldData.size

            override fun getNewListSize(): Int = newData.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldData.getOrNull(oldItemPosition)
                val newItem = newData.getOrNull(newItemPosition)
                return oldItem?.colorString == newItem?.colorString &&
                    oldItem?.isSelected == newItem?.isSelected
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldData.getOrNull(oldItemPosition) == newData.getOrNull(newItemPosition)
        }
}

