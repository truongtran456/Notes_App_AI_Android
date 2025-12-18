package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ItemDrawerChildBinding
import com.philkes.notallyx.databinding.ItemDrawerItemBinding
import com.philkes.notallyx.databinding.ItemDrawerSectionBinding

sealed class DrawerEntry {
    data class Section(val id: String, val title: String) : DrawerEntry()

    data class Child(
        val id: String,
        val title: String,
        val badge: String? = null,
    ) : DrawerEntry()

    data class Item(
        val id: String,
        val title: String,
        val iconRes: Int,
        val badge: String? = null,
    ) : DrawerEntry()
}

class DrawerAdapter(
    private val onItemClick: (DrawerEntry) -> Unit,
) : ListAdapter<DrawerEntry, RecyclerView.ViewHolder>(Diff) {

    var selectedId: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    companion object {
        private const val TYPE_SECTION = 0
        private const val TYPE_CHILD = 1
        private const val TYPE_ITEM = 2
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is DrawerEntry.Section -> TYPE_SECTION
            is DrawerEntry.Child -> TYPE_CHILD
            is DrawerEntry.Item -> TYPE_ITEM
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SECTION ->
                SectionVH(ItemDrawerSectionBinding.inflate(inflater, parent, false))
            TYPE_CHILD ->
                ChildVH(ItemDrawerChildBinding.inflate(inflater, parent, false), onItemClick)
            else ->
                ItemVH(ItemDrawerItemBinding.inflate(inflater, parent, false), onItemClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = getItem(position)) {
            is DrawerEntry.Section -> (holder as SectionVH).bind(entry)
            is DrawerEntry.Child -> (holder as ChildVH).bind(entry)
            is DrawerEntry.Item -> (holder as ItemVH).bind(entry)
        }
    }

    private object Diff : DiffUtil.ItemCallback<DrawerEntry>() {
        override fun areItemsTheSame(oldItem: DrawerEntry, newItem: DrawerEntry): Boolean {
            return when {
                oldItem is DrawerEntry.Section && newItem is DrawerEntry.Section -> oldItem.id == newItem.id
                oldItem is DrawerEntry.Child && newItem is DrawerEntry.Child -> oldItem.id == newItem.id
                oldItem is DrawerEntry.Item && newItem is DrawerEntry.Item -> oldItem.id == newItem.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: DrawerEntry, newItem: DrawerEntry): Boolean {
            return oldItem == newItem
        }
    }

    inner class SectionVH(
        private val binding: ItemDrawerSectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: DrawerEntry.Section) {
            binding.TvSectionTitle.text = entry.title
        }
    }

    inner class ChildVH(
        private val binding: ItemDrawerChildBinding,
        private val onItemClick: (DrawerEntry) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: DrawerEntry.Child) {
            val isSelected = entry.id == selectedId
            applySelected(binding.root, isSelected)
            val selectedColor = color(binding.root, R.color.nav_item_selected)
            val unselectedColor = color(binding.root, R.color.nav_item_unselected)
            binding.TvTitle.text = entry.title
            binding.TvTitle.setTextColor(if (isSelected) selectedColor else unselectedColor)
            binding.TvBadge.apply {
                text = entry.badge
                visibility = if (entry.badge.isNullOrEmpty()) View.GONE else View.VISIBLE
                setTextColor(color(binding.root, R.color.nav_text_secondary))
            }
            binding.root.setOnClickListener { onItemClick(entry) }
        }
    }

    inner class ItemVH(
        private val binding: ItemDrawerItemBinding,
        private val onItemClick: (DrawerEntry) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: DrawerEntry.Item) {
            val isSelected = entry.id == selectedId
            applySelected(binding.root, isSelected)
            val selectedColor = color(binding.root, R.color.nav_item_selected)
            val unselectedColor = color(binding.root, R.color.nav_item_unselected)
            val badgeColor = color(binding.root, R.color.nav_text_secondary)
            binding.TvTitle.text = entry.title
            binding.TvTitle.setTextColor(if (isSelected) selectedColor else unselectedColor)
            binding.IvIcon.setImageResource(entry.iconRes)
            binding.IvIcon.setColorFilter(if (isSelected) selectedColor else unselectedColor)
            binding.TvBadge.apply {
                text = entry.badge
                visibility = if (entry.badge.isNullOrEmpty()) View.GONE else View.VISIBLE
                setTextColor(if (isSelected) selectedColor else badgeColor)
            }
            binding.root.setOnClickListener { onItemClick(entry) }
        }
    }

    private fun applySelected(view: View, isSelected: Boolean) {
        view.setBackgroundResource(
            if (isSelected) R.drawable.bg_drawer_item_selected else R.drawable.bg_drawer_item_normal
        )
    }

    private fun color(view: View, resId: Int): Int = ContextCompat.getColor(view.context, resId)
}

