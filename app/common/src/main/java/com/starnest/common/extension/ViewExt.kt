package com.starnest.common.extension

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.createBitmap
import androidx.core.widget.NestedScrollView
import com.starnest.core.extension.runDelayed
import com.starnest.core.extension.runDelayedOnUiThread
import kotlin.math.max


fun View.toImage(
    image: Bitmap?,
    isUseViewHeightOnly: Boolean = false,
    backgroundColor: Int? = null
): Bitmap {
    if (this is NestedScrollView) {
        return this.getChildAt(0).toImage(image, backgroundColor = backgroundColor)
    }

    val screenHeight = resources.displayMetrics.heightPixels

    val bitmap = createBitmap(width, if (isUseViewHeightOnly) height else max(screenHeight, height))
    val canvas = Canvas(bitmap)
    if (backgroundColor != null) {
        val backgroundTint =  backgroundColor
        val bgDrawable: Drawable = background ?: ColorDrawable().apply {
            backgroundTintList = ColorStateList.valueOf(backgroundTint)
        }
        bgDrawable.draw(canvas)

        canvas.drawColor(backgroundTint)
    }

    image?.let {
        canvas.drawBitmap(
            it,
            null,
            Rect(
                0,
                0,
                width,
                if (isUseViewHeightOnly) height else max(screenHeight, height)
            ),
            null
        )
    }

    draw(canvas)

    return bitmap
}



fun View.startTranslateAnimation(
    delay: Long = 100L,
    fromXDelta: Float = 0f,
    toXDelta: Float = 20f,
    fromYDelta: Float = 0f,
    toYDelta: Float = 0f,
    timeStop: Long = 0L,
) {
    clearAnimation()

    val anim: Animation = TranslateAnimation(
        fromXDelta, toXDelta, fromYDelta, toYDelta
    ).apply {
        duration = delay
        repeatMode = Animation.REVERSE
        repeatCount = 5

        setAnimationListener(
            object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {

                }

                override fun onAnimationEnd(animation: Animation?) {
                    if (timeStop == 0L) {
                        runDelayedOnUiThread(500L) {
                            startAnimation(this@apply)
                        }
                    } else startAnimation(this@apply)

                }

                override fun onAnimationRepeat(animation: Animation?) {
                }

            }
        )

    }
    startAnimation(anim)


    if (timeStop != 0L) {
        runDelayed(timeStop) {
            animation?.setAnimationListener(null)
            animation = null
            clearAnimation()
        }
    }
}

fun View.setMargins(
    marginLeft: Int? = null,
    marginTop: Int? =  null,
    marginRight: Int? = null,
    marginBottom: Int? = null
) {
    val layoutParams = this.layoutParams as ViewGroup.MarginLayoutParams

    marginLeft?.let {
        layoutParams.leftMargin = it
    }

    marginTop?.let {
        layoutParams.topMargin = it
    }

    marginRight?.let {
        layoutParams.rightMargin = it
    }

    marginBottom?.let {
        layoutParams.bottomMargin = it
    }

    this.layoutParams = layoutParams
}

fun View.setSize(width: Int? = null, height: Int? = null) {
    val layoutParams = this.layoutParams ?: return

    if (width != null) {
        layoutParams.width = width
    }

    if (height != null) {
        layoutParams.height = height
    }

    this.layoutParams = layoutParams
}

fun View.startTypingAnimation(
    fullText: String,
    charDelay: Long = 60L,
    finishTime: Long = 1000L,
    onFinished: (() -> Unit)? = null,
) {
    if (this !is TextView) return

    var currentIndex = 0
    val handler = Handler(Looper.getMainLooper())

    val runnable = object : Runnable {
        override fun run() {
            if (currentIndex <= fullText.length) {
                text = fullText.substring(0, currentIndex)
                currentIndex++
                handler.postDelayed(this, charDelay)
            } else {
                //delay after finish
//                handler.postDelayed({
//                    onFinished?.invoke()
//                }, finishTime)
                onFinished?.invoke()
            }
        }
    }
    handler.post(runnable)
}

fun View.setConstraintRatio(ratio: String) {
    val layoutParams = this.layoutParams as? ConstraintLayout.LayoutParams
    layoutParams?.dimensionRatio = ratio
    this.layoutParams = layoutParams
}

fun ViewGroup.setAllTextColor(color: Int) {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        if (child is TextView) {
            child.setTextColor(color)
        } else if (child is ViewGroup) {
            // Recursively apply to nested layouts
            child.setAllTextColor(color)
        }
    }
}

fun View.toRect(): RectF {
    return RectF(0f, 0f, width.toFloat(), height.toFloat())
}