package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.ItemHomeFilterBarBinding

class HomeFilterBarAdapter(
    private val pillAdapter: HomeFilterPillAdapter
) : RecyclerView.Adapter<HomeFilterBarAdapter.FilterBarVH>() {

    private var currentViewHolder: FilterBarVH? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterBarVH {
        val binding = ItemHomeFilterBarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FilterBarVH(binding, pillAdapter).also { currentViewHolder = it }
    }

    override fun onBindViewHolder(holder: FilterBarVH, position: Int) {
        holder.bind()
        currentViewHolder = holder
    }

    override fun getItemCount(): Int = 1

    fun scrollToSelectedPosition(position: Int) {
        currentViewHolder?.scrollToPosition(position)
    }

    class FilterBarVH(
        private val binding: ItemHomeFilterBarBinding,
        private val pillAdapter: HomeFilterPillAdapter
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.FilterPillsRecyclerView.apply {
                if (layoutManager == null) {
                    layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                        context,
                        androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                        false
                    )
                }
                if (adapter == null) {
                    adapter = pillAdapter
                }
            }
        }

        fun scrollToPosition(position: Int) {
            binding.FilterPillsRecyclerView.smoothScrollToPosition(position)
        }
    }
}

