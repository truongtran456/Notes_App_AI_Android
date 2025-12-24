package com.philkes.notallyx.presentation.view.study

/**
 * Trạng thái của Study Set
 */
enum class StudyState {
    NOT_STARTED,    // Chưa học
    IN_PROGRESS,    // Đang học
    COMPLETED       // Hoàn thành
}

/**
 * Data class cho Study Set UI
 */
data class StudySetUI(
    val noteId: Long,
    val title: String,
    val state: StudyState,
    val total: Int = 0,              // Tổng số từ vựng
    val mastered: Int = 0,           // Số từ đã master
    val weak: Int = 0,               // Số từ yếu
    val unlearned: Int = 0,          // Số từ chưa học
    val progressPercent: Int = 0,    // % tiến độ (0-100)
    val lastStudied: Long? = null,   // Timestamp lần học cuối
    val isExpanded: Boolean = false   // Trạng thái expand/collapse
) {
    /**
     * Tính % tiến độ từ mastered và total
     */
    fun calculateProgress(): Int {
        return if (total > 0) {
            ((mastered.toFloat() / total.toFloat()) * 100).toInt()
        } else {
            0
        }
    }
    
    /**
     * Format last studied date
     */
    fun getLastStudiedText(context: android.content.Context): String {
        if (lastStudied == null) return ""
        
        val now = System.currentTimeMillis()
        val diff = now - lastStudied
        val days = diff / (1000 * 60 * 60 * 24)
        
        return when {
            days == 0L -> context.getString(com.philkes.notallyx.R.string.today)
            days == 1L -> context.getString(com.philkes.notallyx.R.string.yesterday)
            days < 7L -> java.text.MessageFormat.format(
                context.getString(com.philkes.notallyx.R.string.days_ago),
                days.toInt()
            )
            else -> {
                val dateFormat = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
                dateFormat.format(java.util.Date(lastStudied))
            }
        }
    }
}

