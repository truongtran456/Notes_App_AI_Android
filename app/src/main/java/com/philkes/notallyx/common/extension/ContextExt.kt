package com.philkes.notallyx.common.extension

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.DisplayMetrics
import android.util.Size
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.annotation.SuppressLint
import androidx.fragment.app.FragmentActivity
import com.philkes.notallyx.R
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

@SuppressLint("InflateParams")
fun Context.showMoreColor(
    success: (Int) -> Unit,
) {
    val dialog = ColorPickerDialog.Builder(this)
        .setTitle(R.string.change_color)
        .setPreferenceName("DrawMoreColorPicker")
        .attachAlphaSlideBar(true)
        .attachBrightnessSlideBar(true)
        .setPositiveButton(
            android.R.string.ok,
            ColorEnvelopeListener { envelope: ColorEnvelope, _: Boolean ->
                success(envelope.color)
            },
        )
        .setNegativeButton(android.R.string.cancel) { d, _ ->
            d.dismiss()
        }
        .create()

    dialog.show()
}

// advanced feedback & popup-menu helpers from original app are not needed for NotallyX draw tool.

fun Context?.isAvailable(): Boolean {
    if (this == null) {
        return false
    } else if (this !is Application) {
        if (this is FragmentActivity) {
            return !this.isDestroyed
                    && !this.isFinishing
        } else if (this is Activity) {
            return !this.isDestroyed && !this.isFinishing
        }
    }
    return true
}

fun Context?.isAvailable(callback: () -> Unit) {
    if (isAvailable()) {
        callback.invoke()
    }
}

fun Context.isInputMethodEnabled(): Boolean {
    try {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val imePackageName: String = packageName
        for (imi in imm.enabledInputMethodList) {
            if (imi.packageName == imePackageName) {
                return true
            }
        }
        return false
    } catch (e: java.lang.Exception) {
        return false
    }
}

// BaseApp from original project not available in NotallyX; orientation helpers below are enough.


val Context.isPortrait: Boolean
    get() = this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

val Context.isLandscape: Boolean
    get() = this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

val Context.isTablet: Boolean
    get() = this.resources.configuration.smallestScreenWidthDp >= 600

val Context.isPhonePortrait: Boolean
    get() = !isTablet && isPortrait

val Context.isPhoneLandscape: Boolean
    get() = !isTablet && isLandscape

val Context.isTabletPortrait: Boolean
    get() = isTablet && isPortrait

val Context.isTabletLandscape: Boolean
    get() = isTablet && isLandscape

fun Context.screenSize(): Size {
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val windowMetrics = windowManager.currentWindowMetrics
        val insets = windowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        val bounds = windowMetrics.bounds
        Size(
            bounds.width() - insets.left - insets.right,
            bounds.height() - insets.top - insets.bottom
        )
    } else {
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }
}