package com.philkes.notallyx.presentation.view.study

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ItemStudySetCardBinding

class StudySetsAdapter(
    private val onItemClick: (StudySetUI) -> Unit,
    private val onActionClick: (StudySetUI) -> Unit
) : ListAdapter<StudySetUI, StudySetsAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(
        private val binding: ItemStudySetCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            studySet: StudySetUI,
            onItemClick: (StudySetUI) -> Unit,
            onActionClick: (StudySetUI) -> Unit
        ) {
            binding.apply {
                // Set title - không viết hoa toàn bộ
                SetTitle.text = studySet.title

                // Set icon based on state với màu động
                val iconResId = when (studySet.state) {
                    StudyState.NOT_STARTED -> R.drawable.notebook
                    StudyState.IN_PROGRESS -> R.drawable.notebook
                    StudyState.COMPLETED -> R.drawable.notebook
                }
                SetIcon.setImageResource(iconResId)
                
                // Set màu icon động dựa trên noteId
                val iconTints = listOf(
                    "#2196F3", // Blue
                    "#FF9800", // Orange/Gold
                    "#4CAF50", // Green
                    "#9C27B0", // Purple
                    "#E91E63"  // Pink
                )
                val colorIndex = (studySet.noteId % iconTints.size).toInt()
                SetIcon.setColorFilter(android.graphics.Color.parseColor(iconTints[colorIndex]))

                // Handle different states
                when (studySet.state) {
                    StudyState.NOT_STARTED -> {
                        // Not started: Show status text, hide progress
                        StatusText.visibility = android.view.View.VISIBLE
                        StatusText.text = root.context.getString(R.string.not_started_yet)
                        StatusText.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                        ProgressPercent.visibility = android.view.View.GONE
                        ProgressBarContainer.visibility = android.view.View.GONE
                        ExpandedDetails.visibility = android.view.View.GONE
                        ActionButton.text = "${root.context.getString(R.string.start_learning)} →"
                        ActionButton.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                    }
                    StudyState.IN_PROGRESS, StudyState.COMPLETED -> {
                        // In progress: Show progress
                        StatusText.visibility = android.view.View.GONE
                        ProgressPercent.visibility = android.view.View.VISIBLE
                        ProgressBarContainer.visibility = android.view.View.VISIBLE
                        ProgressPercent.text = root.context.getString(R.string.progress_completed, studySet.progressPercent)
                        ProgressPercent.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                        
                        // Set progress bar width dynamically
                        ProgressBarContainer.post {
                            val containerWidth = ProgressBarContainer.width
                            val progressWidth = (containerWidth * studySet.progressPercent / 100f).toInt()
                            val layoutParams = ProgressBar.layoutParams
                            layoutParams.width = progressWidth.coerceAtLeast(0)
                            ProgressBar.layoutParams = layoutParams
                        }

                        // Stats details - Always show for IN_PROGRESS
                        ExpandedDetails.visibility = android.view.View.VISIBLE
                        MasteredCount.text = studySet.mastered.toString()
                        WeakCount.text = studySet.weak.toString()
                        NewCount.text = studySet.unlearned.toString()
                        
                        // Hiển thị ngày tạo note thay vì Last studied
                        val creationText =
                            java.text.MessageFormat.format(
                                root.context.getString(R.string.creation_date) + " {0}",
                                studySet.getLastStudiedText(root.context),
                            )
                        LastStudied.text = creationText
                        LastStudied.visibility = android.view.View.VISIBLE

                        ActionButton.text = "${root.context.getString(R.string.continue_learning)} →"
                        ActionButton.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                    }
                }

                // Click listeners
                root.setOnClickListener {
                    onItemClick(studySet)
                }
                ActionButton.setOnClickListener {
                    onActionClick(studySet)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStudySetCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick, onActionClick)
    }

    private class DiffCallback : DiffUtil.ItemCallback<StudySetUI>() {
        override fun areItemsTheSame(oldItem: StudySetUI, newItem: StudySetUI): Boolean {
            return oldItem.noteId == newItem.noteId
        }

        override fun areContentsTheSame(oldItem: StudySetUI, newItem: StudySetUI): Boolean {
            return oldItem == newItem
        }
    }
}

