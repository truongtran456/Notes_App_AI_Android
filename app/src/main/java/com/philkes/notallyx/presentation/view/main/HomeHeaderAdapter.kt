package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.ItemHomeHeaderBinding

class HomeHeaderAdapter : RecyclerView.Adapter<HomeHeaderAdapter.HeaderVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderVH {
        val binding = ItemHomeHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeaderVH(binding)
    }

    override fun onBindViewHolder(holder: HeaderVH, position: Int) {
        // static text, nothing dynamic for now
    }

    override fun getItemCount(): Int = 1

    class HeaderVH(binding: ItemHomeHeaderBinding) : RecyclerView.ViewHolder(binding.root)
}

