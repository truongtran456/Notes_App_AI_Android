package com.philkes.notallyx.presentation.view.misc

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import me.zhanghai.android.fastscroll.FastScrollNestedScrollView

/**
 * Custom NestedScrollView có thể disable scroll hoàn toàn
 */
class NonScrollableNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FastScrollNestedScrollView(context, attrs, defStyleAttr) {

    private var isScrollEnabled: Boolean = true

    fun setScrollEnabled(enabled: Boolean) {
        isScrollEnabled = enabled
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Nếu scroll bị disable, không intercept touch events
        if (!isScrollEnabled) {
            return false // Không intercept, cho phép child views (như canvas) nhận touch events
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Nếu scroll bị disable, không xử lý touch events
        if (!isScrollEnabled) {
            return false // Không xử lý, cho phép child views nhận touch events
        }
        return super.onTouchEvent(ev)
    }
    
    override fun canScrollVertically(direction: Int): Boolean {
        // Nếu scroll bị disable, không thể scroll
        return isScrollEnabled && super.canScrollVertically(direction)
    }
    
    override fun canScrollHorizontally(direction: Int): Boolean {
        // Nếu scroll bị disable, không thể scroll
        return isScrollEnabled && super.canScrollHorizontally(direction)
    }
}

