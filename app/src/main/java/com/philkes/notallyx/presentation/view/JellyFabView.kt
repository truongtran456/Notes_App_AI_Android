package com.philkes.notallyx.presentation.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * JellyFabView - A jelly-like expandable FAB menu inspired by JellyFab
 * Supports two-layer expansion: primary and secondary
 */
class JellyFabView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // State
    private var isPrimaryExpanded = false
    private var isSecondaryExpanded = false
    
    // Primary FABs (3 items: Draw, Text Format, More)
    private val primaryFabs = mutableListOf<FloatingActionButton>()
    private val primaryFabPositions = mutableListOf<PointF>()
    
    // Secondary FABs (4 items: Add Files, Add Images, Record Audio, Link Note)
    private val secondaryFabs = mutableListOf<FloatingActionButton>()
    private val secondaryFabPositions = mutableListOf<PointF>()
    
    // Main FAB
    private lateinit var mainFab: FloatingActionButton
    
    // Scrim layer
    private var scrimView: View? = null
    
    // Animation
    private var primaryAnimator: ValueAnimator? = null
    private var secondaryAnimator: ValueAnimator? = null
    private var scrimAnimator: ValueAnimator? = null
    
    // Configuration
    private val fabSize = 56.dp
    private val fabSpacing = 16.dp
    private val arcRadius = 120.dp
    private val arcStartAngle = -90f // Start from top
    private val arcSweepAngle = 90f // 90 degrees arc
    
    // Jelly blob path (for future enhancement)
    private val jellyPath = Path()
    private val jellyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, android.R.color.transparent)
    }
    
    init {
        clipChildren = false
        clipToPadding = false
        // Đảm bảo view có background trong suốt để không che các view khác
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }
    
    /**
     * Setup main FAB and primary FABs
     */
    fun setup(
        mainFabIcon: Int,
        primaryItems: List<FabItem>,
        secondaryItems: List<FabItem>,
        onMainFabClick: () -> Unit = {}
    ) {
        // Clear existing
        removeAllViews()
        primaryFabs.clear()
        secondaryFabs.clear()
        
        // Create scrim
        scrimView = View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
            alpha = 0f
            visibility = GONE
            setOnClickListener { collapseAll() }
        }
        addView(scrimView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        
        // Create main FAB - đảm bảo hiển thị ngay
        mainFab = FloatingActionButton(context).apply {
            id = View.generateViewId()
            setImageResource(mainFabIcon)
            visibility = VISIBLE
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            // Đảm bảo có elevation
            elevation = 6.dp.toFloat()
            setOnClickListener { 
                if (!isPrimaryExpanded) {
                    expandPrimary()
                } else {
                    collapseAll()
                }
                onMainFabClick()
            }
        }
        val mainFabParams = LayoutParams(fabSize, fabSize).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            // Đặt vị trí ban đầu ở góc dưới bên phải
            setMargins(0, 0, 0, 0)
        }
        addView(mainFab, mainFabParams)
        
        // Đảm bảo mainFab được đặt đúng vị trí ngay lập tức
        post {
            if (width > 0 && height > 0) {
                val centerX = width - fabSize / 2f - 16.dp
                val centerY = height - fabSize / 2f - 16.dp
                mainFab.x = centerX - fabSize / 2f
                mainFab.y = centerY - fabSize / 2f
            }
        }
        
        // Create primary FABs (3 items in arc)
        primaryItems.forEachIndexed { index, item ->
            val fab = FloatingActionButton(context).apply {
                setImageResource(item.iconRes)
                contentDescription = item.contentDescription
                alpha = 0f
                scaleX = 0f
                scaleY = 0f
                visibility = GONE
                setOnClickListener {
                    if (item.isMoreButton) {
                        // Toggle secondary expansion
                        if (!isSecondaryExpanded) {
                            expandSecondary()
                        } else {
                            collapseSecondary()
                        }
                    } else {
                        item.onClick()
                        collapseAll()
                    }
                }
            }
            primaryFabs.add(fab)
            addView(fab, LayoutParams(fabSize, fabSize))
        }
        
        // Create secondary FABs (4 items)
        secondaryItems.forEachIndexed { index, item ->
            val fab = FloatingActionButton(context).apply {
                setImageResource(item.iconRes)
                contentDescription = item.contentDescription
                alpha = 0f
                scaleX = 0f
                scaleY = 0f
                visibility = GONE
                setOnClickListener {
                    item.onClick()
                    collapseAll()
                }
            }
            secondaryFabs.add(fab)
            addView(fab, LayoutParams(fabSize, fabSize))
        }
        
        // Request layout để đảm bảo có kích thước trước khi tính toán
        post {
            calculatePositions()
            updateFabPositions()
        }
    }
    
    private fun calculatePositions() {
        primaryFabPositions.clear()
        secondaryFabPositions.clear()
        
        // Đảm bảo có kích thước trước khi tính toán
        if (width == 0 || height == 0) {
            return
        }
        
        val centerX = width - fabSize / 2f - 16.dp
        val centerY = height - fabSize / 2f - 16.dp
        
        // Primary FABs positions (arc from top-left)
        val primaryCount = primaryFabs.size
        if (primaryCount > 0) {
            val angleStep = arcSweepAngle / (primaryCount + 1)
            primaryFabs.forEachIndexed { index, _ ->
                val angle = Math.toRadians((arcStartAngle + angleStep * (index + 1)).toDouble())
                val x = centerX + arcRadius * cos(angle).toFloat()
                val y = centerY + arcRadius * sin(angle).toFloat()
                primaryFabPositions.add(PointF(x, y))
            }
        }
        
        // Secondary FABs positions (arc from More FAB position)
        val moreFabIndex = primaryFabs.indexOfFirst { 
            it.contentDescription?.contains("more", ignoreCase = true) == true 
        }
        if (moreFabIndex >= 0 && moreFabIndex < primaryFabPositions.size) {
            val moreFabPos = primaryFabPositions[moreFabIndex]
            val secondaryCount = secondaryFabs.size
            if (secondaryCount > 0) {
                val secondaryAngleStep = 60f / (secondaryCount + 1)
                secondaryFabs.forEachIndexed { index, _ ->
                    val angle = Math.toRadians((arcStartAngle + secondaryAngleStep * (index + 1)).toDouble())
                    val x = moreFabPos.x + (arcRadius * 0.7f) * cos(angle).toFloat()
                    val y = moreFabPos.y + (arcRadius * 0.7f) * sin(angle).toFloat()
                    secondaryFabPositions.add(PointF(x, y))
                }
            }
        }
    }
    
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed && width > 0 && height > 0) {
            calculatePositions()
            updateFabPositions()
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Đảm bảo có kích thước tối thiểu để chứa FABs
        val minSize = (fabSize + arcRadius * 2).toInt()
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        
        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> widthSize.coerceAtMost(minSize)
            else -> minSize
        }
        
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> heightSize.coerceAtMost(minSize)
            else -> minSize
        }
        
        setMeasuredDimension(width, height)
    }
    
    private fun updateFabPositions() {
        if (width == 0 || height == 0) {
            return
        }
        
        val centerX = width - fabSize / 2f - 16.dp
        val centerY = height - fabSize / 2f - 16.dp
        
        // Main FAB - đặt ở góc dưới bên phải
        if (::mainFab.isInitialized) {
            mainFab.x = centerX - fabSize / 2f
            mainFab.y = centerY - fabSize / 2f
        }
        
        // Primary FABs - chỉ update nếu đã có positions
        if (primaryFabPositions.isNotEmpty()) {
            primaryFabs.forEachIndexed { index, fab ->
                if (index < primaryFabPositions.size) {
                    val pos = primaryFabPositions[index]
                    fab.x = pos.x - fabSize / 2f
                    fab.y = pos.y - fabSize / 2f
                }
            }
        }
        
        // Secondary FABs - chỉ update nếu đã có positions
        if (secondaryFabPositions.isNotEmpty()) {
            secondaryFabs.forEachIndexed { index, fab ->
                if (index < secondaryFabPositions.size) {
                    val pos = secondaryFabPositions[index]
                    fab.x = pos.x - fabSize / 2f
                    fab.y = pos.y - fabSize / 2f
                }
            }
        }
    }
    
    private fun expandPrimary() {
        if (isPrimaryExpanded) return
        
        isPrimaryExpanded = true
        
        // Show scrim
        scrimView?.let {
            it.visibility = VISIBLE
            scrimAnimator?.cancel()
            scrimAnimator = ValueAnimator.ofFloat(0f, 0.3f).apply {
                duration = 300
                addUpdateListener { animator ->
                    it.alpha = animator.animatedValue as Float
                }
                start()
            }
        }
        
        // Animate primary FABs
        primaryAnimator?.cancel()
        primaryFabs.forEachIndexed { index, fab ->
            fab.visibility = VISIBLE
            fab.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay((index * 50).toLong())
                .setDuration(300)
                .setInterpolator(OvershootInterpolator(1.5f))
                .start()
        }
        
        // Rotate main FAB
        mainFab.animate()
            .rotation(45f)
            .setDuration(300)
            .start()
    }
    
    private fun expandSecondary() {
        if (isSecondaryExpanded || !isPrimaryExpanded) return
        
        isSecondaryExpanded = true
        
        // Animate secondary FABs
        secondaryAnimator?.cancel()
        secondaryFabs.forEachIndexed { index, fab ->
            fab.visibility = VISIBLE
            fab.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay((index * 50).toLong())
                .setDuration(300)
                .setInterpolator(OvershootInterpolator(1.5f))
                .start()
        }
    }
    
    private fun collapseSecondary() {
        if (!isSecondaryExpanded) return
        
        isSecondaryExpanded = false
        
        // Animate secondary FABs out
        secondaryFabs.forEachIndexed { index, fab ->
            fab.animate()
                .alpha(0f)
                .scaleX(0f)
                .scaleY(0f)
                .setStartDelay((index * 30).toLong())
                .setDuration(200)
                .withEndAction {
                    fab.visibility = GONE
                }
                .start()
        }
    }
    
    private fun collapseAllInternal() {
        if (!isPrimaryExpanded) return
        
        isPrimaryExpanded = false
        isSecondaryExpanded = false
        
        // Hide scrim
        scrimView?.let {
            scrimAnimator?.cancel()
            scrimAnimator = ValueAnimator.ofFloat(it.alpha, 0f).apply {
                duration = 200
                addUpdateListener { animator ->
                    it.alpha = animator.animatedValue as Float
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        it.visibility = GONE
                    }
                })
                start()
            }
        }
        
        // Collapse secondary first
        secondaryFabs.forEach { fab ->
            fab.animate()
                .alpha(0f)
                .scaleX(0f)
                .scaleY(0f)
                .setDuration(150)
                .withEndAction {
                    fab.visibility = GONE
                }
                .start()
        }
        
        // Collapse primary
        primaryFabs.forEachIndexed { index, fab ->
            fab.animate()
                .alpha(0f)
                .scaleX(0f)
                .scaleY(0f)
                .setStartDelay((index * 30).toLong())
                .setDuration(200)
                .withEndAction {
                    fab.visibility = GONE
                }
                .start()
        }
        
        // Rotate main FAB back
        mainFab.animate()
            .rotation(0f)
            .setDuration(300)
            .start()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Future: Draw jelly blob path here
    }
    
    fun isPrimaryExpanded(): Boolean = isPrimaryExpanded
    
    fun isSecondaryExpanded(): Boolean = isSecondaryExpanded
    
    fun collapseAll() {
        collapseAllInternal()
    }
    
    data class FabItem(
        val iconRes: Int,
        val contentDescription: String,
        val onClick: () -> Unit,
        val isMoreButton: Boolean = false
    )
}
