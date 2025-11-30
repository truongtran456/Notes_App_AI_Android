package com.philkes.notallyx.core.ui.adapter

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.common.model.Selectable
import com.philkes.notallyx.core.data.model.RecyclerItem

abstract class TMVVMAdapter<T>(open var list: ArrayList<T>) :
    RecyclerView.Adapter<TMVVMViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TMVVMViewHolder {
        return onCreateViewHolderBase(parent, viewType)
    }

    override fun onBindViewHolder(holder: TMVVMViewHolder, position: Int) {
        onBindViewHolderBase(holder, position)
    }

    abstract fun onCreateViewHolderBase(parent: ViewGroup?, viewType: Int): TMVVMViewHolder

    abstract fun onBindViewHolderBase(holder: TMVVMViewHolder?, position: Int)

    open fun getDiffCallback(oldData: List<T>, newData: List<T>): DiffUtil.Callback? {
        return null
    }

    @SuppressLint("NotifyDataSetChanged")
    open fun updateListItems(data: List<T>) {
        val diffCallback = getDiffCallback(this.list, data)

        if (diffCallback != null) {
            try {
                val diffResult = DiffUtil.calculateDiff(diffCallback)
                this.list.clear()
                this.list.addAll(data)
                diffResult.dispatchUpdatesTo(this)
            } catch (e: Exception) {
                this.list.clear()
                this.list.addAll(data)
                notifyDataSetChanged()
            }
        } else {
            this.list.clear()
            this.list.addAll(data)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun getData(): List<T> {
        return list
    }

    override fun getItemViewType(position: Int): Int {
        return (list[position] as? RecyclerItem<*>)?.type?.value
            ?: RecyclerItem.ViewType.NORMAL.value
    }

    fun <T : Selectable> TMVVMAdapter<T>.reloadChangedItems(predicate: (T) -> Boolean): List<Int> {
        val previousIndex = list.indexOfFirst { it.isSelected == true }
        val currentIndex = list.indexOfFirst { predicate(it) }

        if (previousIndex != currentIndex) {
            for (item in list) {
                item.isSelected = predicate(item)
            }
            notifyItemChanged(previousIndex)
            notifyItemChanged(currentIndex)

            return arrayListOf(previousIndex, currentIndex)
        }

        return arrayListOf()
    }

}

