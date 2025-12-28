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
                // Set màu #FEFDFF cho card (Learned và Not Started)
                val cardColor = android.graphics.Color.parseColor("#FEFDFF")
                root.setCardBackgroundColor(cardColor)
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
                try {
                    SetIcon.setColorFilter(android.graphics.Color.parseColor(iconTints[colorIndex]))
                } catch (e: Exception) {
                    // Fallback to default color if parsing fails
                    e.printStackTrace()
                }

                // Handle different states
                when (studySet.state) {
                    StudyState.NOT_STARTED -> {
                        // Not started: Show status text, hide progress
                        StatusText.visibility = android.view.View.VISIBLE
                        StatusText.text = root.context.getString(R.string.not_started_yet)
                        try {
                            StatusText.setTextColor(android.graphics.Color.parseColor("#9787FF"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        ProgressPercent.visibility = android.view.View.GONE
                        ProgressBarContainer.visibility = android.view.View.GONE
                        ExpandedDetails.visibility = android.view.View.GONE
                        ActionButton.text = "${root.context.getString(R.string.start_learning)} →"
                        try {
                            ActionButton.setTextColor(android.graphics.Color.parseColor("#9787FF"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    StudyState.IN_PROGRESS, StudyState.COMPLETED -> {
                        // In progress: Show progress
                        StatusText.visibility = android.view.View.GONE
                        ProgressPercent.visibility = android.view.View.VISIBLE
                        ProgressBarContainer.visibility = android.view.View.VISIBLE
                        val progressPercent = studySet.progressPercent.coerceIn(0, 100)
                        ProgressPercent.text = root.context.getString(R.string.progress_completed, progressPercent)
                        try {
                            ProgressPercent.setTextColor(android.graphics.Color.parseColor("#9787FF"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        // Set progress bar width dynamically
                        ProgressBarContainer.post {
                            try {
                                // Check if view is still attached
                                if (ProgressBarContainer.width > 0 && ProgressBarContainer.isAttachedToWindow) {
                                    val containerWidth = ProgressBarContainer.width
                                    val progressPercent = studySet.progressPercent.coerceIn(0, 100)
                                    val progressWidth = (containerWidth * progressPercent / 100f).toInt()
                                    val layoutParams = ProgressBar.layoutParams
                                    if (layoutParams != null) {
                                        layoutParams.width = progressWidth.coerceAtLeast(0)
                                        ProgressBar.layoutParams = layoutParams
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore errors if view is detached
                                e.printStackTrace()
                            }
                        }

                        // Stats details - Always show for IN_PROGRESS with emoji
                        // Hiển thị giống checklist: Learning (●) / Weak (📚) / Learned (✓)
                        ExpandedDetails.visibility = android.view.View.VISIBLE
                        MasteredCount.text = "✓ ${studySet.mastered}"  // Learned
                        WeakCount.text = "📚 ${studySet.weak}"          // Weak
                        NewCount.text = "● ${studySet.unlearned}"       // Learning
                        
                        // Hiển thị ngày tạo note thay vì Last studied
                        val creationText =
                            java.text.MessageFormat.format(
                                root.context.getString(R.string.creation_date) + " {0}",
                                studySet.getLastStudiedText(root.context),
                            )
                        LastStudied.text = creationText
                        LastStudied.visibility = android.view.View.VISIBLE

                        ActionButton.text = "${root.context.getString(R.string.continue_learning)} →"
                        try {
                            ActionButton.setTextColor(android.graphics.Color.parseColor("#9787FF"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // Click listeners - use safe click listeners
                root.setOnClickListener {
                    try {
                        onItemClick(studySet)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                ActionButton.setOnClickListener {
                    try {
                        onActionClick(studySet)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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

