package com.philkes.notallyx.presentation.view.ai

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.philkes.notallyx.R

/**
 * Modern AI Summary Card với Segmented Control + Swipe
 * 
 * Design: Material You 2024-2025
 * - Segmented control có tick để chọn Gốc/Tóm tắt
 * - Swipe gesture để chuyển đổi nhanh
 * - Animation mượt mà, hiện đại
 */
class AiSlideCompareView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    // UI Components
    private val swipeContainer: FrameLayout
    private val originalScrollView: ScrollView
    private val summaryScrollView: ScrollView
    private val originalContainer: LinearLayout
    private val summaryContainer: LinearLayout
    private val originalText: TextView
    private val summaryText: TextView
    
    // Segmented Control
    private val buttonOriginal: FrameLayout
    private val buttonSummary: FrameLayout
    private val buttonOriginalBackground: MaterialCardView
    private val buttonSummaryBackground: MaterialCardView
    private val iconOriginalTick: ImageView
    private val iconSummaryTick: ImageView
    private val textOriginal: TextView
    private val textSummary: TextView
    
    // Apply Button
    private val buttonApply: com.google.android.material.button.MaterialButton
    
    // Hint
    private val swipeHint: LinearLayout

    // State
    private var isShowingSummary: Boolean = false
    private var hintDismissCount: Int = 0
    
    // Callback khi user chọn bản nào làm chính
    var onSelectionChanged: ((isShowingSummary: Boolean) -> Unit)? = null
    
    // Callback khi user ấn nút Áp dụng
    var onApplyClicked: ((selectedText: String, isShowingSummary: Boolean) -> Unit)? = null
    
    // Gesture
    private val gestureDetector: GestureDetectorCompat

    init {
        LayoutInflater.from(context).inflate(R.layout.view_ai_slide_compare, this, true)

        // Bind views
        swipeContainer = findViewById(R.id.SwipeContainer)
        originalScrollView = findViewById(R.id.OriginalScrollView)
        summaryScrollView = findViewById(R.id.SummaryScrollView)
        originalContainer = findViewById(R.id.OriginalContainer)
        summaryContainer = findViewById(R.id.SummaryContainer)
        originalText = findViewById(R.id.OriginalText)
        summaryText = findViewById(R.id.SummaryText)
        
        buttonOriginal = findViewById(R.id.ButtonOriginal)
        buttonSummary = findViewById(R.id.ButtonSummary)
        buttonOriginalBackground = findViewById(R.id.ButtonOriginalBackground)
        buttonSummaryBackground = findViewById(R.id.ButtonSummaryBackground)
        iconOriginalTick = findViewById(R.id.IconOriginalTick)
        iconSummaryTick = findViewById(R.id.IconSummaryTick)
        textOriginal = findViewById(R.id.TextOriginal)
        textSummary = findViewById(R.id.TextSummary)
        
        buttonApply = findViewById(R.id.ButtonApply)
        
        swipeHint = findViewById(R.id.SwipeHint)

        // Setup gesture detector
        gestureDetector = GestureDetectorCompat(context, SwipeGestureListener())
        
        // Setup click listeners
        buttonOriginal.setOnClickListener {
            selectOriginal(animated = true)
        }
        
        buttonSummary.setOnClickListener {
            selectSummary(animated = true)
        }
        
        // Setup apply button
        buttonApply.setOnClickListener {
            // Animation khi ấn
            it.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(100)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
            
            val selectedText = getCurrentText()
            onApplyClicked?.invoke(selectedText, isShowingSummary)
        }
        
        // Setup swipe on container
        swipeContainer.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false // Cho phép scroll view xử lý scroll
        }

        // Initial state: show original
        updateUIState(animated = false)
    }

    fun setTexts(original: String, summary: String) {
        originalText.text = original
        summaryText.text = summary
    }

    fun selectOriginal(animated: Boolean = true) {
        if (!isShowingSummary) return
        isShowingSummary = false
        updateUIState(animated)
        dismissHintIfNeeded()
        onSelectionChanged?.invoke(false)
    }

    fun selectSummary(animated: Boolean = true) {
        if (isShowingSummary) return
        isShowingSummary = true
        updateUIState(animated)
        dismissHintIfNeeded()
        onSelectionChanged?.invoke(true)
    }
    
    fun getCurrentText(): String {
        return if (isShowingSummary) {
            summaryText.text.toString()
        } else {
            originalText.text.toString()
        }
    }
    
    fun isShowingSummaryState(): Boolean = isShowingSummary

    fun toggle() {
        if (isShowingSummary) {
            selectOriginal(animated = true)
        } else {
            selectSummary(animated = true)
        }
    }

    fun reset(animated: Boolean = false) {
        isShowingSummary = false
        updateUIState(animated)
    }

    fun showHintOnce(prefs: SharedPreferences) {
        val seen = prefs.getBoolean(PREF_KEY_HINT_SEEN, false)
        if (!seen) {
            swipeHint.visibility = View.VISIBLE
            swipeHint.alpha = 0.4f
            prefs.edit().putBoolean(PREF_KEY_HINT_SEEN, true).apply()
        } else {
            swipeHint.visibility = View.GONE
        }
    }

    private fun updateUIState(animated: Boolean) {
        val duration = if (animated) 220L else 0L
        
        if (isShowingSummary) {
            // Hiển thị Summary
            updateSegmentedControl(
                originalActive = false,
                summaryActive = true,
                duration = duration
            )
            crossfadeContent(
                hideView = originalScrollView,
                showView = summaryScrollView,
                duration = duration
            )
        } else {
            // Hiển thị Original
            updateSegmentedControl(
                originalActive = true,
                summaryActive = false,
                duration = duration
            )
            crossfadeContent(
                hideView = summaryScrollView,
                showView = originalScrollView,
                duration = duration
            )
        }
    }

    private fun updateSegmentedControl(
        originalActive: Boolean,
        summaryActive: Boolean,
        duration: Long
    ) {
        val interpolator = AccelerateDecelerateInterpolator()
        
        // Original button
        if (originalActive) {
            buttonOriginalBackground.visibility = View.VISIBLE
            buttonOriginalBackground.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start()
            iconOriginalTick.visibility = View.VISIBLE
            iconOriginalTick.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start()
            textOriginal.animate()
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withStartAction {
                    textOriginal.setTextColor(0xFF9787FF.toInt())
                    textOriginal.paint.isFakeBoldText = true
                    textOriginal.invalidate()
                }
                .start()
        } else {
            buttonOriginalBackground.animate()
                .alpha(0f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction {
                    buttonOriginalBackground.visibility = View.INVISIBLE
                    buttonOriginalBackground.scaleX = 1f
                    buttonOriginalBackground.scaleY = 1f
                }
                .start()
            iconOriginalTick.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(duration / 2)
                .setInterpolator(interpolator)
                .withEndAction {
                    iconOriginalTick.visibility = View.GONE
                    iconOriginalTick.scaleX = 1f
                    iconOriginalTick.scaleY = 1f
                }
                .start()
            textOriginal.animate()
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withStartAction {
                    textOriginal.setTextColor(0xFF666666.toInt())
                    textOriginal.paint.isFakeBoldText = false
                    textOriginal.invalidate()
                }
                .start()
        }
        
        // Summary button
        if (summaryActive) {
            buttonSummaryBackground.visibility = View.VISIBLE
            buttonSummaryBackground.setCardBackgroundColor(0x1A9787FF)
            buttonSummaryBackground.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start()
            iconSummaryTick.visibility = View.VISIBLE
            iconSummaryTick.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start()
            textSummary.animate()
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withStartAction {
                    textSummary.setTextColor(0xFF9787FF.toInt())
                    textSummary.paint.isFakeBoldText = true
                    textSummary.invalidate()
                }
                .start()
        } else {
            buttonSummaryBackground.animate()
                .alpha(0f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction {
                    buttonSummaryBackground.visibility = View.INVISIBLE
                    buttonSummaryBackground.scaleX = 1f
                    buttonSummaryBackground.scaleY = 1f
                }
                .start()
            iconSummaryTick.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(duration / 2)
                .setInterpolator(interpolator)
                .withEndAction {
                    iconSummaryTick.visibility = View.GONE
                    iconSummaryTick.scaleX = 1f
                    iconSummaryTick.scaleY = 1f
                }
                .start()
            textSummary.animate()
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withStartAction {
                    textSummary.setTextColor(0xFF666666.toInt())
                    textSummary.paint.isFakeBoldText = false
                    textSummary.invalidate()
                }
                .start()
        }
    }

    private fun crossfadeContent(
        hideView: View,
        showView: View,
        duration: Long
    ) {
        if (duration == 0L) {
            hideView.visibility = View.GONE
            hideView.alpha = 0f
            showView.visibility = View.VISIBLE
            showView.alpha = 1f
            return
        }
        
        // Smooth crossfade với slight translation
        val translateDistance = 12f * resources.displayMetrics.density
        
        // Fade out + translate
        hideView.animate()
            .alpha(0f)
            .translationX(if (isShowingSummary) -translateDistance else translateDistance)
            .setDuration(duration / 2)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                hideView.visibility = View.GONE
                hideView.translationX = 0f
                
                // Fade in + translate
                showView.visibility = View.VISIBLE
                showView.alpha = 0f
                showView.translationX = if (isShowingSummary) translateDistance else -translateDistance
                showView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(duration / 2)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun dismissHintIfNeeded() {
        hintDismissCount++
        if (hintDismissCount >= 2 && swipeHint.isVisible) {
            swipeHint.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    swipeHint.visibility = View.GONE
                }
                .start()
        }
    }

    /**
     * Gesture listener cho swipe
     */
    private inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y
            
            // Chỉ xử lý swipe ngang
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && 
                    Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    
                    if (diffX > 0) {
                        // Swipe right -> show original
                        selectOriginal(animated = true)
                    } else {
                        // Swipe left -> show summary
                        selectSummary(animated = true)
                    }
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val PREF_KEY_HINT_SEEN = "ai_slide_compare_hint_seen_v2"
    }
}
