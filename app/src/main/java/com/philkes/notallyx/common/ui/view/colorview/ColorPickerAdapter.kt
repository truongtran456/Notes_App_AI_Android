package com.philkes.notallyx.common.ui.view.colorview

import android.content.Context
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import com.philkes.notallyx.common.extension.color
import com.philkes.notallyx.common.extension.debounceClick
import com.philkes.notallyx.common.extension.gone
import com.philkes.notallyx.common.extension.reloadChangedItems
import com.philkes.notallyx.common.extension.showMoreColor
import com.philkes.notallyx.common.extension.rawColor
import com.philkes.notallyx.core.ui.adapter.TMVVMAdapter
import com.philkes.notallyx.core.ui.adapter.TMVVMViewHolder
import com.philkes.notallyx.databinding.ItemColorItemLayoutBinding
import com.philkes.notallyx.databinding.ItemColorItemMoreLayoutBinding
import com.philkes.notallyx.databinding.ItemColorItemNoneLayoutBinding


class ColorPickerAdapter(val context: Context, val listener: OnItemClickListener) :
    TMVVMAdapter<ColorPickerItem>(list = ArrayList()) {

    interface OnItemClickListener {
        fun onItemClick(color: ColorPickerItem)
    }

    object ViewType {
        const val COLOR = 0
        const val COLOR_MORE = 1
        const val COLOR_NONE = 2
    }

    override fun onCreateViewHolderBase(
        parent: ViewGroup?,
        viewType: Int
    ): TMVVMViewHolder {
        return when (viewType) {
            ViewType.COLOR_MORE -> {
                val binding = ItemColorItemMoreLayoutBinding.inflate(
                    LayoutInflater.from(parent?.context), parent, false
                )
                TMVVMViewHolder(binding)
            }

            ViewType.COLOR_NONE -> {
                val binding = ItemColorItemNoneLayoutBinding.inflate(
                    LayoutInflater.from(parent?.context), parent, false
                )
                TMVVMViewHolder(binding)
            }

            else -> {
                val binding = ItemColorItemLayoutBinding.inflate(
                    LayoutInflater.from(parent?.context), parent, false
                )
                TMVVMViewHolder(binding)
            }
        }
    }


    override fun onBindViewHolderBase(
        holder: TMVVMViewHolder?,
        position: Int
    ) {
        val viewType = getItemViewType(position)
        when (viewType) {
            ViewType.COLOR_MORE -> {
                configColorMoreLayout(holder = holder, position = position)
            }

            ViewType.COLOR_NONE -> {
                configColorNoneLayout(holder = holder, position = position)
            }
            else -> {
                configColorLayout(holder = holder, position = position)
            }
        }
    }


    private fun configColorMoreLayout(holder: TMVVMViewHolder?, position: Int) {
        val color = list.getOrNull(position) ?: return
        val binding = holder?.binding as ItemColorItemMoreLayoutBinding

        binding.clImageSelected.gone(!color.isSelected)
        binding.root.debounceClick {
            context.showMoreColor(success = { colorInt ->
                val tempColor = color
                tempColor.colorString = colorInt.rawColor()
                list[position] = tempColor

                reloadChangedItems {
                    it.colorString == tempColor.colorString
                }
                listener.onItemClick(tempColor)
            })
        }
    }

    private fun configColorNoneLayout(holder: TMVVMViewHolder?, position: Int) {
        val color = list.getOrNull(position) ?: return
        val binding = holder?.binding as ItemColorItemNoneLayoutBinding

        binding.clImageSelected.gone(!color.isSelected)
        binding.root.debounceClick {
            reloadChangedItems {
                it.colorString == color.colorString
            }
            listener.onItemClick(color)
        }

    }
    private fun configColorLayout(holder: TMVVMViewHolder?, position: Int) {
        val color = list.getOrNull(position) ?: return
        val binding = holder?.binding as ItemColorItemLayoutBinding

        val bgColor =
            ContextCompat.getDrawable(context, com.philkes.notallyx.R.drawable.bg_item_color_normal) as LayerDrawable

        bgColor.findDrawableByLayerId(com.philkes.notallyx.R.id.background).setTint(color.colorString.color)

        binding.clImageSelected.gone(!color.isSelected)
        binding.clImage.background = bgColor
        binding.root.debounceClick {
            reloadChangedItems {
                it.colorString == color.colorString
            }
            listener.onItemClick(color)
        }
//        binding.setVariable(BR.color, color)
//        binding.executePendingBindings()
    }

    override fun getItemViewType(position: Int): Int {
        val color = list.getOrNull(position) ?: return ViewType.COLOR
        return if (color.isMore) {
            ViewType.COLOR_MORE
        } else if (color.isNone) {
            ViewType.COLOR_NONE
        } else {
            ViewType.COLOR
        }
    }

    override fun getDiffCallback(
        oldData: List<ColorPickerItem>,
        newData: List<ColorPickerItem>
    ): DiffUtil.Callback? {
        return object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return oldData.size
            }

            override fun getNewListSize(): Int {
                return newData.size
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldData.getOrNull(oldItemPosition)
                val newItem = newData.getOrNull(newItemPosition)
                return oldItem?.colorString == newItem?.colorString && oldItem?.isSelected == newItem?.isSelected
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldData.getOrNull(oldItemPosition) == newData.getOrNull(newItemPosition)
            }
        }
    }
}