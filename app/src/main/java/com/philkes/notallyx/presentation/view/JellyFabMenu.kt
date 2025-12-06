package com.philkes.notallyx.presentation.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton

class JellyFabMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CoordinatorLayout(context, attrs, defStyleAttr) {

    init {
        // Cho phép child FAB vươn ra ngoài mà không bị cắt
        clipChildren = false
        clipToPadding = false
    }

    private var mainFab: FloatingActionButton? = null
    private val childFabs = mutableListOf<FloatingActionButton>()
    private var scrimView: View? = null
    private var scrimParent: ViewGroup? = null // View cha để add scrim (thường là RelativeLayout)
    private var isExpanded = false
    private var isAnimating = false // Flag để tránh expand/collapse khi đang animate
    private var animationDuration = 300L
    private var overshootTension = 2.0f
    
    fun isExpanded(): Boolean = isExpanded

    override fun onFinishInflate() {
        super.onFinishInflate()
        setupViews()
    }

    private fun setupViews() {
        // Tìm tất cả FABs trong layout
        val fabList = mutableListOf<FloatingActionButton>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is FloatingActionButton) {
                fabList.add(child)
            }
        }
        
        // Main FAB là FAB đầu tiên trong layout (MainFab)
        // Child FABs là các FAB còn lại (TakeNote, MakeList)
        if (fabList.isNotEmpty()) {
            mainFab = fabList[0] // MainFab là FAB đầu tiên
            // KHÔNG set click listener ở đây vì MainActivity đã set rồi
            
            // Các FAB còn lại là child FABs (bắt đầu từ index 1)
            childFabs.clear()
            if (fabList.size > 1) {
                childFabs.addAll(fabList.subList(1, fabList.size))
            }
            
            // Đảm bảo child FABs được setup đúng
            childFabs.forEach { fab ->
                fab.bringToFront()
                fab.visibility = View.INVISIBLE // Dùng INVISIBLE thay vì GONE để view vẫn được layout
                fab.alpha = 0f
                fab.scaleX = 0f
                fab.scaleY = 0f
                fab.elevation = 20f // Đảm bảo FAB ở trên scrim (cao hơn scrim)
                fab.isClickable = true
                fab.isFocusable = true
                fab.isEnabled = true
                // QUAN TRỌNG: Đảm bảo FAB có thể nhận touch events
                fab.isPressed = false
            }
        }
    }

    fun toggle() {
        // Không cho phép toggle khi đang animate
        if (isAnimating) return
        
        if (isExpanded) {
            collapse()
        } else {
            expand()
        }
    }

    fun expand() {
        if (isExpanded || isAnimating) return

        setupViews()
        if (mainFab == null || childFabs.isEmpty()) return

        isExpanded = true
        isAnimating = true

        showScrim()

        // Rotate main FAB
        mainFab?.animate()
            ?.rotation(45f)
            ?.setDuration(animationDuration)
            ?.setInterpolator(OvershootInterpolator(overshootTension))
            ?.start()

        val spacing = 72.dpToPx()

        childFabs.forEachIndexed { index, fab ->
            fab.visibility = View.VISIBLE
            fab.isClickable = true
            fab.isFocusable = true
            fab.isEnabled = true
            fab.elevation = 20f // Đảm bảo FAB ở trên scrim
            fab.bringToFront() // Đảm bảo FAB ở trên cùng
            fab.translationY = 0f
            fab.scaleX = 0f
            fab.scaleY = 0f
            fab.alpha = 0f
            
            // QUAN TRỌNG: Đảm bảo click listeners không bị mất
            // MainActivity đã set click listeners, nhưng cần đảm bảo chúng vẫn hoạt động
            fab.isClickable = true
            fab.isEnabled = true

            fab.animate()
                .translationY(-spacing * (index + 1))
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setStartDelay(index * 50L)
                .setDuration(animationDuration)
                .setInterpolator(OvershootInterpolator(overshootTension))
                .withEndAction {
                    if (index == childFabs.lastIndex) {
                        isAnimating = false
                        // Đảm bảo vị trí cuối cùng và FAB có thể click được
                        childFabs.forEachIndexed { i, child ->
                            child.translationY = -spacing * (i + 1)
                            child.alpha = 1f
                            child.scaleX = 1f
                            child.scaleY = 1f
                            child.visibility = View.VISIBLE
                            child.isClickable = true
                            child.isEnabled = true
                            child.elevation = 20f
                            child.bringToFront()
                            // Đảm bảo FAB có thể nhận touch events
                            child.isPressed = false
                        }
                        // Đảm bảo JellyFabMenu và các FAB ở trên scrim
                        this@JellyFabMenu.bringToFront()
                        mainFab?.bringToFront()
                        childFabs.forEach { it.bringToFront() }
                        
                        // Đảm bảo FAB phụ có thể nhận click events
                        ensureChildFabsClickable()
                    }
                }
                .start()
        }
    }

    fun collapse() {
        if (!isExpanded || isAnimating) return
        isExpanded = false
        isAnimating = true

        hideScrim()

        mainFab?.animate()
            ?.rotation(0f)
            ?.setDuration(animationDuration)
            ?.setInterpolator(OvershootInterpolator(overshootTension))
            ?.start()

        childFabs.reversed().forEachIndexed { revIndex, fab ->
            fab.animate()
                .translationY(0f)
                .scaleX(0f)
                .scaleY(0f)
                .alpha(0f)
                .setStartDelay(revIndex * 40L)
                .setDuration((animationDuration * 0.8).toLong())
                .setInterpolator(OvershootInterpolator(overshootTension * 0.6f))
                .withEndAction {
                    fab.visibility = View.INVISIBLE
                    if (revIndex == childFabs.lastIndex) {
                        isAnimating = false
                        // Reset vị trí sau khi collapse
                        childFabs.forEach { child ->
                            child.translationY = 0f
                            child.alpha = 0f
                            child.scaleX = 0f
                            child.scaleY = 0f
                        }
                    }
                }
                .start()
        }
    }

    /**
     * Cho phép MainActivity gọi thủ công khi startActivity để tránh chặn click do isAnimating
     */
    fun forceCollapse() {
        isExpanded = false
        isAnimating = false
        hideScrim()
        mainFab?.rotation = 0f
        childFabs.forEach { child ->
            child.visibility = View.INVISIBLE
            child.translationY = 0f
            child.alpha = 0f
            child.scaleX = 0f
            child.scaleY = 0f
        }
    }

    private fun showScrim() {
        val parent = scrimParent ?: (this.parent as? ViewGroup) ?: return
        
        if (scrimView == null) {
            scrimView = View(context).apply {
                setBackgroundColor(0x80000000.toInt()) // Semi-transparent black
                alpha = 0f
                // QUAN TRỌNG: Set isClickable = false để scrim không chặn touch events của FAB
                // FABs sẽ nhận touch events vì chúng có elevation cao hơn và nằm trên scrim trong z-order
                isClickable = false
                isFocusable = false
                elevation = 0f // Đảm bảo scrim ở dưới FABs
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            // Add scrim vào parent (RelativeLayout) để cover toàn bộ màn hình
            // Add scrim trước JellyFabMenu để scrim nằm dưới JellyFabMenu trong z-order
            val jellyFabIndex = parent.indexOfChild(this)
            if (jellyFabIndex >= 0) {
                parent.addView(scrimView, jellyFabIndex) // Add trước JellyFabMenu
            } else {
                parent.addView(scrimView) // Fallback: add vào cuối
            }
            // KHÔNG set click listener cho scrim để tránh chặn touch events của FAB
            // Scrim chỉ để dim background, không cần click để collapse
            // User có thể click vào main FAB để collapse, hoặc click vào child FAB để thực hiện action
        }

        scrimView?.visibility = View.VISIBLE
        scrimView?.animate()
            ?.alpha(1f)
            ?.setDuration(animationDuration)
            ?.start()
    }

    private fun hideScrim() {
        scrimView?.isClickable = false
        scrimView?.animate()
            ?.alpha(0f)
            ?.setDuration(animationDuration)
            ?.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    scrimView?.visibility = View.GONE
                }
            })
            ?.start()
    }
    
    fun setScrimParent(parent: ViewGroup) {
        scrimParent = parent
    }
    
    /**
     * Đảm bảo FAB phụ có thể nhận click events sau khi expand
     * Method này có thể được gọi từ MainActivity sau khi expand hoàn tất
     */
    fun ensureChildFabsClickable() {
        childFabs.forEach { fab ->
            fab.isClickable = true
            fab.isEnabled = true
            fab.isFocusable = true
            fab.bringToFront()
        }
        this.bringToFront()
        mainFab?.bringToFront()
    }

    private fun Int.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }

    fun setMainFab(fab: FloatingActionButton) {
        mainFab = fab
        fab.setOnClickListener { toggle() }
    }

    fun addChildFab(fab: FloatingActionButton) {
        childFabs.add(fab)
        fab.visibility = View.GONE
        fab.alpha = 0f
        fab.scaleX = 0f
        fab.scaleY = 0f
    }
}

