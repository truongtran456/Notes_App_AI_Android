package com.philkes.notallyx.presentation.view.note.ai

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Enum định nghĩa các loại AI option
 */
enum class AIOptionType {
    SUMMARY,        // Summary/Summary Table
    KEY,            // Key Points/Bullet Points/Story
    QUESTION,       // Questions/Flashcards
    MCQ,            // MCQ
    CLOZE,          // Cloze Test
    MATCH           // Match Pairs
}

/**
 * Data class chứa thông tin của mỗi AI option
 */
@Parcelize
data class AIOption(
    val iconResId: Int,           // ID của icon
    val titleResId: Int,          // ID của text label
    val type: AIOptionType
): Parcelable {
    companion object {
        /**
         * Hàm tạo danh sách mặc định 4 options cho text notes
         */
        fun getDefaultForText(): ArrayList<AIOption> {
            return arrayListOf(
                AIOption(
                    iconResId = com.philkes.notallyx.R.drawable.summary,
                    titleResId = com.philkes.notallyx.R.string.ai_action_summary,
                    type = AIOptionType.SUMMARY
                ),
                AIOption(
                    iconResId = com.philkes.notallyx.R.drawable.key,
                    titleResId = com.philkes.notallyx.R.string.ai_action_bullet,
                    type = AIOptionType.KEY
                ),
                AIOption(
                    iconResId = com.philkes.notallyx.R.drawable.question,
                    titleResId = com.philkes.notallyx.R.string.ai_action_questions,
                    type = AIOptionType.QUESTION
                ),
                AIOption(
                    iconResId = com.philkes.notallyx.R.drawable.mcq,
                    titleResId = com.philkes.notallyx.R.string.ai_action_mcq,
                    type = AIOptionType.MCQ
                )
            )
        }

        /**
         * Hàm tạo danh sách mặc định cho vocab/checklist
         * (đã bỏ SUMMARY TABLE vì hiển thị inline ngay trong checklist)
         */
        fun getDefaultForVocab(): ArrayList<AIOption> {
            return arrayListOf(
                AIOption(
                    iconResId = com.philkes.notallyx.R.drawable.ai_sparkle,
                    titleResId = com.philkes.notallyx.R.string.ai_vocab_story,
                    type = AIOptionType.KEY
                ),
                AIOption(
                    iconResId = com.philkes.notallyx.R.drawable.document_scanner,
                    titleResId = com.philkes.notallyx.R.string.ai_vocab_flashcards,
                    type = AIOptionType.QUESTION
                ),
                AIOption(
                    iconResId = com.philkes.notallyx.R.drawable.ic_checkbox_list,
                    titleResId = com.philkes.notallyx.R.string.ai_vocab_quiz,
                    type = AIOptionType.MCQ
                ),
                AIOption(
                    iconResId = com.philkes.notallyx.R.drawable.ic_checkbox_list,
                    titleResId = com.philkes.notallyx.R.string.ai_vocab_cloze,
                    type = AIOptionType.CLOZE
                ),
                AIOption(
                    iconResId = com.philkes.notallyx.R.drawable.ic_checkbox_list,
                    titleResId = com.philkes.notallyx.R.string.ai_vocab_match_pairs,
                    type = AIOptionType.MATCH
                )
            )
        }
    }
}

