package com.philkes.notallyx.draw.ui.background

import android.content.Context
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.common.extension.gone
import com.philkes.notallyx.common.extension.showMoreColor
import com.philkes.notallyx.common.extension.rawColor
import com.philkes.notallyx.common.ui.view.colorview.ColorPickerItem
import com.philkes.notallyx.core.ui.adapter.TMVVMAdapter
import com.philkes.notallyx.core.ui.adapter.TMVVMViewHolder
import com.philkes.notallyx.databinding.ItemColorCustomItemLayoutBinding

class CustomColorAdapter(
    val context: Context,
    private val listener: OnItemClickListener,
) : TMVVMAdapter<ColorCustomItem>(arrayListOf()) {

    interface OnItemClickListener {
        fun onItemClick(colorIndex: Int)
        fun onMoreClick()
        fun onAddClick(colorString: String)
        fun onDeleteClick(colorIndex: Int)
        fun onUpdateColorClick(oldColor: ColorCustomItem, newColorString: String)
    }

    object ViewType {
        const val COLOR = 0
        const val COLOR_MORE = 1
        const val COLOR_ADD = 2
    }

    var isEditMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int {
        val color = list.getOrNull(position) ?: return ViewType.COLOR
        return when {
            color.isMore -> ViewType.COLOR_MORE
            color.isAddColor -> ViewType.COLOR_ADD
            else -> ViewType.COLOR
        }
    }

    override fun onCreateViewHolderBase(
        parent: ViewGroup?,
        viewType: Int,
    ): TMVVMViewHolder {
        val inflater = LayoutInflater.from(parent?.context)
        val binding =
            ItemColorCustomItemLayoutBinding.inflate(inflater, parent, false)
        return TMVVMViewHolder(binding)
    }

    override fun onBindViewHolderBase(
        holder: TMVVMViewHolder?,
        position: Int
    ) {
        when (getItemViewType(position)) {
            ViewType.COLOR_MORE -> configColorMoreLayout(holder, position)
            ViewType.COLOR_ADD -> configColorAddLayout(holder, position)
            else -> configColorLayout(holder, position)
        }
    }

    private fun configColorMoreLayout(holder: RecyclerView.ViewHolder?, position: Int) {
        val binding = (holder as TMVVMViewHolder).binding as ItemColorCustomItemLayoutBinding
        binding.clImageSelected.gone(true)
        binding.ivDelete.gone(true)
        // Ô "More color": dùng trực tiếp drawable bánh xe màu, không chèn icon lên vòng tròn
        binding.ivIcon.gone(true)
        binding.clImage.background =
            ContextCompat.getDrawable(context, R.drawable.ic_item_color_wheel)

        binding.root.setOnClickListener {
            context.showMoreColor(success = { colorInt ->
                val selectedColorString = colorInt.rawColor()
                listener.onAddClick(selectedColorString)
                listener.onMoreClick()
            })
        }
    }

    private fun configColorAddLayout(holder: RecyclerView.ViewHolder?, position: Int) {
        val binding = (holder as TMVVMViewHolder).binding as ItemColorCustomItemLayoutBinding
        binding.clImageSelected.gone(true)
        binding.ivDelete.gone(true)
        // Icon "+" cho ô thêm màu
        binding.ivIcon.visibility = android.view.View.VISIBLE
        binding.ivIcon.setImageResource(R.drawable.ic_item_color_add)

        binding.root.setOnClickListener {
            context.showMoreColor(success = { colorInt ->
                val selectedColorString = colorInt.rawColor()
                listener.onAddClick(selectedColorString)
            })
        }
    }

    private fun configColorLayout(holder: RecyclerView.ViewHolder?, position: Int) {
        val color = list.getOrNull(position) ?: return
        val binding = (holder as TMVVMViewHolder).binding as ItemColorCustomItemLayoutBinding

        // Màu thường: ẩn icon, tô nền theo color
        binding.ivIcon.gone(true)

        val bgColor =
            ContextCompat.getDrawable(context, R.drawable.bg_item_color_normal) as LayerDrawable
        bgColor.findDrawableByLayerId(R.id.background).setTint(color.color)
        binding.clImage.background = bgColor

        binding.clImageSelected.gone(!color.isSelected)
        binding.ivDelete.visibility = if (isEditMode) android.view.View.VISIBLE else android.view.View.GONE

        binding.ivDelete.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                listener.onDeleteClick(currentPosition)
            }
        }

        binding.root.setOnClickListener {
            if (isEditMode) {
                context.showMoreColor(success = { colorInt ->
                    val selectedColorString = colorInt.rawColor()
                    listener.onUpdateColorClick(color, selectedColorString)
                })
            } else {
                val currentIndex = holder.bindingAdapterPosition
                if (currentIndex != RecyclerView.NO_POSITION) {
                    listener.onItemClick(currentIndex)
                }
            }
        }
    }
}


