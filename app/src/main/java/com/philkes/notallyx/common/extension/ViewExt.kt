package com.philkes.notallyx.common.extension

import android.content.Context
import android.content.res.Resources
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

const val CLICK_DELAY = 300L

fun View.debounceClick(
    throttleDelay: Long = CLICK_DELAY,
    onClick: (View) -> Unit
) {
    setOnClickListener {
        onClick(this)
        isClickable = false
        postDelayed({ isClickable = true }, throttleDelay)
    }
}


fun View.show(isShow: Boolean = true) {
    visibility = if (isShow) View.VISIBLE else View.INVISIBLE
}

fun View.show() {
    visibility = View.VISIBLE
}

fun View.hide() {
    visibility = View.INVISIBLE
}

fun View.gone(isGone: Boolean = true) {
    visibility = if (isGone) View.GONE else View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun viewGones(vararg views: View) {
    for (view in views) {
        view.gone()
    }
}

fun viewVisibles(vararg views: View) {
    for (view in views) {
        view.show()
    }
}

fun View.showKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    if (this.requestFocus()) {
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }
}

fun View.hideKeyboard(): Boolean {
    try {
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
    } catch (ignored: RuntimeException) {
    }
    return false
}

inline fun View.showSnackBar(snackbarText: String, timeLength: Int, moreSetup: (Snackbar) -> Unit) {
    val snackBar = Snackbar.make(this, snackbarText, timeLength)
    moreSetup(snackBar)
    snackBar.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar?>() {
        override fun onShown(transientBottomBar: Snackbar?) {
            snackBar.removeCallback(this)
        }
    })
    snackBar.show()
}

//inline fun View.debounceClick(timePrevent: Int = 1000, crossinline block: (View) -> Unit) {
//    var lastTimeClicked: Long = 0
//
//    setOnClickListener {
//        if (SystemClock.elapsedRealtime() - lastTimeClicked < timePrevent) {
//            return@setOnClickListener
//        }
//        lastTimeClicked = SystemClock.elapsedRealtime()
//        block(it)
//    }
//}

val Int.dp: Int
    get() = (this / Resources.getSystem().displayMetrics.density).toInt()

val Int.sp: Int
    get() = (this / Resources.getSystem().displayMetrics.scaledDensity).toInt()

val Int.px: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()


val Float.dp: Float
    get() = (this / Resources.getSystem().displayMetrics.density)

val Float.sp: Float
    get() = (this / Resources.getSystem().displayMetrics.scaledDensity)

val Float.px: Float
    get() = (this * Resources.getSystem().displayMetrics.density)


fun View.onGlobalLayout(callback: () -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            callback()
        }
    })
}

fun View.setClickEffect(borderless: Boolean) {
    val attrs = if (borderless) {
        intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
    } else {
        intArrayOf(android.R.attr.selectableItemBackground)
    }

    val typedArray = context.obtainStyledAttributes(attrs)

    val backgroundResource = typedArray.getResourceId(0, 0)

    setBackgroundResource(backgroundResource)

    typedArray.recycle()
}

fun View.performHapticFeedback() = performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

fun View.getSizeOfView(): Size {
    this.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    return Size(this.measuredWidth, this.measuredHeight)
}


/**
 * Animation duration for view animations in milliseconds
 */
private const val SLIDE_ANIMATION_DURATION = 300L

/**
 * Shows the view with a slide-up animation from bottom
 */
fun View.slideIn() {
    visibility = View.VISIBLE
    startAnimation(createSlideAnimation(true))
}

/**
 * Hides the view with a slide-down animation to bottom
 */
fun View.slideOut() {
    val animation = createSlideAnimation(false)
    animation.setAnimationListener(object : SimpleAnimationListener() {
        override fun onAnimationEnd(animation: Animation?) {
            gone()
        }
    })
    startAnimation(animation)
}

/**
 * Creates a slide animation
 * @param isSlideUp true for slide-up, false for slide-down
 * @return AnimationSet containing translate and alpha animations
 */
private fun createSlideAnimation(isSlideUp: Boolean): Animation {
    val animationSet = AnimationSet(true)

    // Create translate animation
    val fromY = if (isSlideUp) 1f else 0f
    val toY = if (isSlideUp) 0f else 1f
    val translateAnimation = TranslateAnimation(
        Animation.RELATIVE_TO_PARENT, 0f,
        Animation.RELATIVE_TO_PARENT, 0f,
        Animation.RELATIVE_TO_PARENT, fromY,
        Animation.RELATIVE_TO_PARENT, toY
    )

    // Create alpha animation
    val fromAlpha = if (isSlideUp) 0f else 1f
    val toAlpha = if (isSlideUp) 1f else 0f
    val alphaAnimation = AlphaAnimation(fromAlpha, toAlpha)

    // Set animation properties
    translateAnimation.duration = SLIDE_ANIMATION_DURATION
    alphaAnimation.duration = SLIDE_ANIMATION_DURATION

    // Add animations to the set
    animationSet.addAnimation(translateAnimation)
    animationSet.addAnimation(alphaAnimation)

    return animationSet
}

/**
 * Simple animation listener with empty implementations
 */
private open class SimpleAnimationListener : Animation.AnimationListener {
    override fun onAnimationStart(animation: Animation?) {}
    override fun onAnimationRepeat(animation: Animation?) {}
    override fun onAnimationEnd(animation: Animation?) {}
}


val View.lifecycleScope: CoroutineScope?
    get() = findViewTreeLifecycleOwner()?.lifecycleScope


fun View.stopScaleAnimation() {
    animation?.setAnimationListener(null)
    animation = null
    clearAnimation()
    scaleX = 1f
    scaleY = 1f
}

fun View.startScaleAnimation(
    delay: Long = 200L,
    defaultScale: Float = 0.95f,
    pivotXValue: Float = 0.5f,
    pivotYValue: Float = 0.5f,
    isStopped: (() -> Boolean)? = null
) {
    clearAnimation()
    val startOffset = 1f
    val endOffset = 1 / defaultScale

    scaleX = defaultScale
    scaleY = defaultScale

    val anim: Animation = ScaleAnimation(
        startOffset, endOffset, startOffset, endOffset,
        Animation.RELATIVE_TO_SELF, pivotXValue, // Pivot point of X scaling
        Animation.RELATIVE_TO_SELF, pivotYValue
    ).apply {
        duration = delay
        repeatMode = Animation.REVERSE
        repeatCount = 5

        setAnimationListener(
            object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {

                }

                override fun onAnimationEnd(animation: Animation?) {
                        postDelayed({
                            if (isStopped?.invoke() == true) {
                                return@postDelayed
                            }
                            startAnimation(this@apply)
                        }, 500L)
                }

                override fun onAnimationRepeat(animation: Animation?) {
                }

            }
        )

    }
    startAnimation(anim)
}


fun View.keyboardVisibilityChanges(): Flow<Boolean> {
    return onPreDrawFlow()
        .map { isKeyboardVisible() }
        .distinctUntilChanged()
}

fun View.onPreDrawFlow(): Flow<Unit> {
    return callbackFlow {
        val onPreDrawListener = ViewTreeObserver.OnPreDrawListener {
            trySendBlocking(Unit)
            true
        }
        viewTreeObserver.addOnPreDrawListener(onPreDrawListener)
        awaitClose {
            viewTreeObserver.removeOnPreDrawListener(onPreDrawListener)
        }
    }
}
fun View.isKeyboardVisible(): Boolean = ViewCompat.getRootWindowInsets(this)
    ?.isVisible(WindowInsetsCompat.Type.ime())
    ?: false



fun View.startTranslateAnimation(
    delay: Long = 100L,
    toXDelta: Float = 20f,
    toYDelta: Float = 0f,
    isRepeat:Boolean = true
) {
    clearAnimation()

    val anim: Animation = TranslateAnimation(
        0f, toXDelta, 0f, toYDelta
    ).apply {
        duration = delay
        repeatMode = Animation.REVERSE
        repeatCount = 5

        setAnimationListener(
            object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {

                }

                override fun onAnimationEnd(animation: Animation?) {
                    if (isRepeat) {
                        postDelayed({
                            startAnimation(this@apply)
                        }, 500L)
                    }

                }

                override fun onAnimationRepeat(animation: Animation?) {
                }

            }
        )

    }
    startAnimation(anim)
}
