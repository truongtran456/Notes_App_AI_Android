package com.philkes.notallyx.presentation.view.note.ai

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.ItemMenuAiOptionBinding

class AIOptionsAdapter(
    private val context: Context,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<AIOptionsAdapter.ViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(option: AIOption)
    }

    private var options: ArrayList<AIOption> = arrayListOf()

    fun updateList(newOptions: ArrayList<AIOption>) {
        options = newOptions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMenuAiOptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = options.getOrNull(position) ?: return
        holder.bind(item)
    }

    override fun getItemCount(): Int = options.size

    inner class ViewHolder(
        private val binding: ItemMenuAiOptionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(option: AIOption) {
            binding.apply {
                // Icon + text
                ivIcon.setImageResource(option.iconResId)
                tvTitle.setText(option.titleResId)

                root.setOnClickListener {
                    listener.onItemClick(option)
                }
            }
        }
    }
}

