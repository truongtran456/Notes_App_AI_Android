package com.philkes.notallyx.draw.ui.newdraw.view.drawtool

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.philkes.notallyx.BR
import com.philkes.notallyx.common.extension.debounceClick
import com.philkes.notallyx.common.model.DrawToolBrush
import com.philkes.notallyx.common.model.DrawToolPenType
import com.philkes.notallyx.core.ui.adapter.TMVVMAdapter
import com.philkes.notallyx.core.ui.adapter.TMVVMViewHolder
import com.philkes.notallyx.databinding.ItemDrawAddPenLayoutBinding
import com.philkes.notallyx.databinding.ItemDrawPickerIconBinding

enum class PenType {
    ADD_PEN, DEFAULT_PEN, ERASER
}

class DrawToolPickerAdapter(
    private val context: Context,
    private val listener: OnItemClickListener
) : TMVVMAdapter<DrawToolBrush>(arrayListOf()) {

    interface OnItemClickListener {
        fun onClick(tool: DrawToolBrush)
        fun onToolEditDetails(tool: DrawToolBrush)
        fun onDelete(tool: DrawToolBrush)
    }

    object ViewType {
        const val NORMAL = 0
        const val ADD = 1
    }

    var isEditModeEnabled: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int {
        val item = list.getOrNull(position) ?: return ViewType.NORMAL
        if (item.isAdd) {
            return ViewType.ADD
        }
        return ViewType.NORMAL
    }

    override fun onCreateViewHolderBase(parent: ViewGroup?, viewType: Int): TMVVMViewHolder {
        return when (viewType) {
            ViewType.ADD -> {
                val binding = ItemDrawAddPenLayoutBinding.inflate(
                    LayoutInflater.from(parent?.context),
                    parent,
                    false
                )
                TMVVMViewHolder(binding)
            }
            else -> {
                val binding = ItemDrawPickerIconBinding.inflate(
                    LayoutInflater.from(parent?.context),
                    parent,
                    false
                )
                TMVVMViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolderBase(holder: TMVVMViewHolder?, position: Int) {
        val viewType = getItemViewType(position)
        when (viewType) {
            ViewType.ADD -> configCustomPenLayout(holder, position)
            else -> configDefaultPenLayout(holder, position)
        }
    }

    private fun configCustomPenLayout(holder: TMVVMViewHolder?, position: Int) {
        val item = list.getOrNull(position) ?: return
        val binding = holder?.binding as ItemDrawAddPenLayoutBinding
        binding.addPenView.isSelected = item.isSelected
        binding.addPenView.debounceClick {
            itemSelected(type = PenType.ADD_PEN, position)
        }
        binding.setVariable(BR.drawToolBrush, item)
        binding.executePendingBindings()
    }

    private fun configDefaultPenLayout(holder: TMVVMViewHolder?, position: Int) {
        val item = list.getOrNull(position) ?: return
        val binding = holder?.binding as ItemDrawPickerIconBinding
        binding.penView.isSelected = item.isSelected

        if (item.type == DrawToolPenType.CUSTOM && isEditModeEnabled) {
            binding.ivDelete.apply {
                visibility = View.VISIBLE
                debounceClick {
                    listener.onDelete(item)
                }
            }
        } else {
            binding.ivDelete.visibility = View.GONE
        }

        binding.penView.debounceClick {
            itemSelected(type = PenType.DEFAULT_PEN, position)
        }
        binding.setVariable(BR.drawToolBrush, item)
        binding.executePendingBindings()
    }

    private fun itemSelected(type: PenType, position: Int) {
        val currentItem = list.getOrNull(position) ?: return
        if (currentItem.isSelected) {
            listener.onToolEditDetails(currentItem)
        } else {
            toggleSelectItem(position)
        }
    }

    private fun toggleSelectItem(position: Int) {
        val currentItem = list.getOrNull(position) ?: return
        val previousIndex = list.indexOfFirst { it.isSelected }
        if (previousIndex >= 0 && previousIndex != position) {
            list[previousIndex].isSelected = false
            notifyItemChanged(previousIndex)
        }
        if (!currentItem.isSelected) {
            list[position].isSelected = true
            notifyItemChanged(position)
        }
        listener.onClick(currentItem)
    }

    fun clearSelection() {
        for ((index, item) in list.withIndex()) {
            if (item.isSelected) {
                item.isSelected = false
                notifyItemChanged(index)
            }
        }
    }
}

