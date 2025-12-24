package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.databinding.RecyclerPinnedNoteBinding
import com.philkes.notallyx.presentation.view.misc.ItemListener
import com.philkes.notallyx.presentation.viewmodel.preference.DateFormat
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortBy

class PinnedNoteAdapter(
    private val selectedIds: Set<Long>,
    private val dateFormat: DateFormat,
    private val preferences: BaseNoteVHPreferences,
    private val imageRoot: java.io.File?,
    private val listener: ItemListener,
    private val onNoteClick: (BaseNote) -> Unit,
    private val onNoteLongClick: (BaseNote) -> Unit,
) : ListAdapter<BaseNote, PinnedNoteAdapter.PinnedNoteViewHolder>(PinnedNoteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinnedNoteViewHolder {
        val binding = RecyclerPinnedNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PinnedNoteViewHolder(binding, dateFormat, preferences, listener)
    }

    override fun onBindViewHolder(holder: PinnedNoteViewHolder, position: Int) {
        val note = getItem(position)
        holder.bind(note, imageRoot, selectedIds.contains(note.id), NotesSortBy.CREATION_DATE)
    }

    override fun onViewRecycled(holder: PinnedNoteViewHolder) {
        super.onViewRecycled(holder)
        // Cleanup Glide requests when view is recycled to prevent memory leaks
        holder.clearGlideRequests()
    }

    inner class PinnedNoteViewHolder(
        private val binding: RecyclerPinnedNoteBinding,
        private val dateFormat: DateFormat,
        private val preferences: BaseNoteVHPreferences,
        listener: ItemListener,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentNote: BaseNote? = null
        
        init {
            binding.root.setOnClickListener {
                currentNote?.let { note ->
                    onNoteClick(note)
                }
            }
            binding.root.setOnLongClickListener {
                currentNote?.let { note ->
                    onNoteLongClick(note)
                }
                true
            }
        }

        private val viewHolder = PinnedNoteVH(
            binding, 
            dateFormat, 
            preferences, 
            object : ItemListener {
                override fun onClick(position: Int) {
                    // Không dùng, đã xử lý trong init
                }

                override fun onLongClick(position: Int) {
                    // Không dùng, đã xử lý trong init
                }
            }
        )

        fun bind(
            baseNote: BaseNote,
            imageRoot: java.io.File?,
            checked: Boolean,
            sortBy: NotesSortBy,
        ) {
            currentNote = baseNote
            // Set lại click listener sau khi bind để đảm bảo currentNote đã được set
            binding.root.setOnClickListener {
                currentNote?.let { note ->
                    onNoteClick(note)
                }
            }
            binding.root.setOnLongClickListener {
                currentNote?.let { note ->
                    onNoteLongClick(note)
                }
                true
            }
            viewHolder.bind(baseNote, imageRoot, checked, sortBy)
        }
        
        fun clearGlideRequests() {
            viewHolder.clearGlideRequests()
        }
    }

    private class PinnedNoteDiffCallback : DiffUtil.ItemCallback<BaseNote>() {
        override fun areItemsTheSame(oldItem: BaseNote, newItem: BaseNote): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BaseNote, newItem: BaseNote): Boolean {
            return oldItem == newItem
        }
    }
}

