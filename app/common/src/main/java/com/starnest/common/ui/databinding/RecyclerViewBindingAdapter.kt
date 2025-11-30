package com.starnest.common.ui.databinding

import androidx.databinding.BindingAdapter
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.starnest.core.extension.lifecycleScope
import kotlinx.coroutines.launch

object RecyclerBindingAdapter {
    @BindingAdapter(value = ["pagingData"])
    @JvmStatic
    fun <T : Any> setPagingData(recyclerView: RecyclerView, items: PagingData<T>?) {
        if (recyclerView.adapter == null) {
            return
        }
        val adapter: PagingDataAdapter<T, *>? = recyclerView.adapter as? PagingDataAdapter<T, *>

        items?.let {
            recyclerView.lifecycleScope?.launch {
                adapter?.submitData(pagingData = it)
            }
        }
    }
}
