package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.databinding.ItemPinnedCarouselBinding

class PinnedCarouselAdapter(
    private val onClick: (BaseNote) -> Unit,
) : ListAdapter<BaseNote, PinnedCarouselAdapter.ViewHolder>(Diff) {

    // Cache gradient for each note ID to ensure consistency and avoid random lag
    private val noteGradientCache = mutableMapOf<Long, Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPinnedCarouselBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val note = getItem(position)
        // Use cached gradient or assign using GradientDistributor for better distribution
        val gradientRes = noteGradientCache.getOrPut(note.id) {
            GradientDistributor.getGradientForNoteWithOffset(note.id, position)
        }
        holder.bind(note, gradientRes)
    }
    
    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        // Clear any heavy references if needed
    }

    class ViewHolder(
        private val binding: ItemPinnedCarouselBinding,
        private val onClick: (BaseNote) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(note: BaseNote, gradientRes: Int) {
            binding.root.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.root.setBackgroundResource(gradientRes)
            binding.Title.text = note.title.ifEmpty { binding.root.context.getString(com.philkes.notallyx.R.string.empty_note) }
            binding.Body.text = when {
                note.type == com.philkes.notallyx.data.model.Type.LIST && note.items.isNotEmpty() ->
                    note.items.take(2).joinToString(" ") { "• ${it.body.take(30)}" }
                note.body.isNotBlank() -> note.body.take(80)
                else -> ""
            }
            binding.root.setOnClickListener { onClick(note) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<BaseNote>() {
        override fun areItemsTheSame(oldItem: BaseNote, newItem: BaseNote): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BaseNote, newItem: BaseNote): Boolean = oldItem == newItem
    }
}

