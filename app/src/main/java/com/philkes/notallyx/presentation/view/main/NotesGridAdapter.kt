package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.databinding.ItemNewNoteCardBinding
import com.philkes.notallyx.databinding.RecyclerBaseNoteBinding
import com.philkes.notallyx.presentation.view.misc.ItemListener
import com.philkes.notallyx.presentation.viewmodel.preference.DateFormat
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortBy
import java.io.File

class NotesGridAdapter(
    private val baseNoteAdapter: BaseNoteAdapter,
    private val onNewNoteClick: () -> Unit,
    private val onNoteClick: (Int) -> Unit,
    private val onNoteLongClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val adapterObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            notifyDataSetChanged()
        }
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            notifyItemRangeInserted(positionStart + 1, itemCount) // +1 for "New note"
        }
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            notifyItemRangeRemoved(positionStart + 1, itemCount) // +1 for "New note"
        }
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            notifyItemRangeChanged(positionStart + 1, itemCount) // +1 for "New note"
        }
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            notifyItemMoved(fromPosition + 1, toPosition + 1) // +1 for "New note"
        }
    }

    init {
        baseNoteAdapter.registerAdapterDataObserver(adapterObserver)
    }

    companion object {
        private const val VIEW_TYPE_NEW_NOTE = 0
        private const val VIEW_TYPE_NOTE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_NEW_NOTE else VIEW_TYPE_NOTE
    }

    override fun getItemCount(): Int {
        return 1 + baseNoteAdapter.itemCount // 1 for "New note" + all notes
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_NEW_NOTE -> {
                val binding = ItemNewNoteCardBinding.inflate(inflater, parent, false)
                NewNoteViewHolder(binding, onNewNoteClick)
            }
            else -> {
                // Delegate to baseNoteAdapter để tạo ViewHolder đúng cách
                val baseVH = baseNoteAdapter.onCreateViewHolder(parent, 1) as BaseNoteVH
                NoteViewHolder(baseVH, onNoteClick, onNoteLongClick)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is NewNoteViewHolder -> holder.bind()
            is NoteViewHolder -> {
                // Delegate to baseNoteAdapter, adjust position by -1
                val basePosition = position - 1
                baseNoteAdapter.onBindViewHolder(holder.baseVH, basePosition)
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            when (holder) {
                is NoteViewHolder -> {
                    val basePosition = position - 1
                    baseNoteAdapter.onBindViewHolder(holder.baseVH, basePosition, payloads)
                }
                else -> onBindViewHolder(holder, position)
            }
        }
    }
    
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        // Cleanup listeners to prevent memory leaks
        if (holder is NewNoteViewHolder) {
            holder.onRecycled()
        }
    }

    fun getItem(position: Int): Item? {
        if (position == 0) return null // "New note" is not an Item
        return baseNoteAdapter.getItem(position - 1)
    }

    class NewNoteViewHolder(
        private val binding: ItemNewNoteCardBinding,
        private val onClick: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private var layoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null
        
        init {
            binding.root.setOnClickListener { onClick() }
            // Set chiều cao = chiều rộng để tạo hình vuông
            binding.root.post {
                val width = binding.root.width
                if (width > 0) {
                    binding.root.layoutParams.height = width
                    binding.root.requestLayout()
                }
            }
            // Lắng nghe thay đổi kích thước để đảm bảo luôn vuông
            layoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
                val width = binding.root.width
                if (width > 0 && binding.root.height != width) {
                    binding.root.layoutParams.height = width
                    binding.root.requestLayout()
                }
            }
            binding.root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
        
        fun onRecycled() {
            // Remove listener to prevent memory leaks
            layoutListener?.let { listener ->
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
            layoutListener = null
        }

        fun bind() {
            // Đảm bảo chiều cao = chiều rộng sau khi bind
            binding.root.post {
                val width = binding.root.width
                if (width > 0) {
                    binding.root.layoutParams.height = width
                    binding.root.requestLayout()
                }
            }
        }
    }

    class NoteViewHolder(
        val baseVH: BaseNoteVH,
        private val onNoteClick: (Int) -> Unit,
        private val onNoteLongClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(baseVH.binding.root) {
        init {
            // Override click listener để điều chỉnh position
            // Remove tất cả click listeners cũ và set listener mới
            baseVH.binding.root.setOnClickListener(null)
            baseVH.binding.root.setOnLongClickListener(null)
            baseVH.binding.root.isClickable = true
            baseVH.binding.root.isFocusable = true
            baseVH.binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position > 0) {
                    // Position trong NotesGridAdapter, truyền trực tiếp để onClick xử lý
                    onNoteClick(position)
                }
            }
            baseVH.binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position > 0) {
                    // Position trong NotesGridAdapter, truyền trực tiếp để onLongClick xử lý
                    onNoteLongClick(position)
                }
                true
            }
        }
    }
}

