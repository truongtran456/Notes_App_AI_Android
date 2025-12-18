package com.philkes.notallyx.presentation.view.main

import com.philkes.notallyx.R

/**
 * Utility để phân phối gradient cho notes một cách ngẫu nhiên nhưng không trùng
 * Sử dụng hash function để đảm bảo mỗi note ID có gradient nhất quán
 */
object GradientDistributor {
    
    val gradients = listOf(
        R.drawable.bg_task_card_gradient_1,
        R.drawable.bg_task_card_gradient_2,
        R.drawable.bg_task_card_gradient_3,
        R.drawable.bg_task_card_gradient_4,
        R.drawable.bg_task_card_gradient_5,
    )
    
    /**
     * Lấy gradient cho note dựa trên ID
     * Sử dụng hash function để phân phối đều và tránh trùng lặp
     */
    fun getGradientForNote(noteId: Long): Int {
        // Sử dụng hash function tốt để phân phối đều
        val hash = noteId.hashCode()
        // Dùng absolute value và mod để đảm bảo index hợp lệ
        val index = (hash and 0x7FFFFFFF).mod(gradients.size)
        return gradients[index]
    }
    
    /**
     * Lấy gradient cho note với offset để tránh trùng với notes khác trong cùng list
     */
    fun getGradientForNoteWithOffset(noteId: Long, position: Int, offset: Int = 0): Int {
        // Kết hợp note ID và position để có phân phối tốt hơn
        val combined = (noteId.hashCode() + position * 31 + offset * 17)
        val index = (combined and 0x7FFFFFFF).mod(gradients.size)
        return gradients[index]
    }
}

