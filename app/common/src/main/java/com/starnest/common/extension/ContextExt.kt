package com.starnest.common.extension

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.util.DisplayMetrics
import android.util.Size
import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.iterator
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.color.colorpicker.dialogs.ColorPickerDialog
import com.color.colorpicker.utils.ColorEx
import com.starnest.common.app.BaseApp
import com.starnest.common.ui.dialog.AppDialogFragment
import com.starnest.common.ui.dialog.DeleteDialogFragment
import com.starnest.common.util.TextRecognizerUtil
import com.starnest.config.model.Config
import com.starnest.core.extension.getColorFromAttr
import com.starnest.core.extension.isTablet
import com.starnest.core.extension.showAllowingStateLoss
import com.starnest.core.extension.showFeedback
import com.starnest.core.extension.toast
import com.starnest.resources.R
import kotlin.collections.iterator
import androidx.core.graphics.toColorInt
import com.starnest.core.extension.color
import com.starnest.core.extension.currentActivity

fun Context.showMoreColorInCompose(
    defaultColor: Int = Color.BLACK,
    success: (Int) -> Unit,
    cancel: (() -> Unit)? = null
) {
    val activity = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext as? AppCompatActivity
        else -> null
    } ?: return
    val fm = activity.supportFragmentManager

    val dialog = ColorPickerDialog.newInstance(
        title = getString(com.color.colorpicker.R.string.colors),
        initColor = ColorEx.toHexEncoding(defaultColor),
        listener = object : ColorPickerDialog.ColorPickerDialogListener {
            override fun onColorChanged(color: String) {
                success.invoke(color.toColorInt())
            }

            override fun onClose() {
                cancel?.invoke()
            }
        }
    )
    dialog.showAllowingStateLoss(fm)
}

fun Context.showMoreColor(
    defaultColor: Int = Color.BLACK,
    success: (Int) -> Unit,
    cancel: (() -> Unit)? = null
) {
    val fm = app.currentActivity?.supportFragmentManager ?: return

    val dialog = ColorPickerDialog.newInstance(
        title = getString(com.color.colorpicker.R.string.colors),
        initColor = ColorEx.toHexEncoding(defaultColor),
        listener = object : ColorPickerDialog.ColorPickerDialogListener {
            override fun onColorChanged(color: String) {
                success.invoke(color.toColorInt())
            }

            override fun onClose() {
                cancel?.invoke()
            }
        }
    )
    dialog.showAllowingStateLoss(fm)
}

fun Context.showPopupMenu(
    view: View,
    @MenuRes menuId: Int,
    callback: (Int) -> Boolean,
    forceShowIcon: Boolean = false,
    mapTitle: HashMap<Int, MenuItem> = HashMap(),
    themeResId: Int = R.style.Theme_Main_PopupMenu
) {
    val wrapper = ContextThemeWrapper(this, themeResId)

    val popupMenu = PopupMenu(wrapper, view).apply {
        setOnMenuItemClickListener {
            callback.invoke(it.itemId)
        }
        inflate(menuId)
    }

    if (forceShowIcon) {
        for (menu in popupMenu.menu.iterator()) {
            val icon = mapTitle[menu.itemId]?.icon ?: menu.icon.apply {
                this?.setTint(getColorFromAttr(com.starnest.core.R.attr.primaryColor))
            }
            icon?.let {
                val iconSize = resources.getDimensionPixelSize(com.starnest.core.R.dimen.dp_24)
                it.setBounds(0, 0, iconSize, iconSize)
                val imageSpan = ImageSpan(it)
                val ssb =
                    SpannableStringBuilder("     " + (mapTitle[menu.itemId]?.title ?: menu.title))
                ssb.setSpan(imageSpan, 1, 2, 0)
                menu.title = ssb
                menu.icon = null
            }
        }
    }
    popupMenu.show()
}

fun Context.showFeedback(versionName: String) {
    showFeedback(
        title = "Feedback for Draw!",
        subject = "Draw Android App Feedback",
        body = "Feedback here:\n\n\n\n\n\n\n" +
                "App: $versionName" +
                "\nAndroid: ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})" +
                "\nDevice name: ${Build.MODEL}",
        to = Config.Feedback.TO,
        cc = Config.Feedback.CC
    )
}

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

val Context.app
    get() = this.applicationContext as BaseApp


fun Context.showDefaultDialog(
    fragmentManager: FragmentManager,
    title: String = "",
    message: String = "",
    positiveTitle: String = getString(com.starnest.core.R.string.ok),
    positiveCallback: (() -> Unit)? = null,
    negativeTitle: String? = null,
    negativeCallback: (() -> Unit)? = null,
    dismissCallback: (() -> Unit)? = null,
    isDismissClickOutside: Boolean = true,
    maxWidth: Int = -1,
    isDeleteDialog: Boolean = false
) {

    AppDialogFragment.newInstance(
        title, message, positiveTitle, negativeTitle,
        callback = object : AppDialogFragment.DialogCallback {
            override fun onNegativeClick() {
                negativeCallback?.invoke()
            }

            override fun onPositiveClick() {
                positiveCallback?.invoke()
            }

            override fun onCancel() {
                dismissCallback?.invoke()
            }
        },
        isDismissClickOutside = isDismissClickOutside,
        maxWidth = maxWidth,
        isDeleteDialog = isDeleteDialog
    ).apply {
        showAllowingStateLoss(fragmentManager, "")
    }
}

fun Context.showDeleteDialog(
    fragmentManager: FragmentManager,
    title: String = "",
    message: String = "",
    positiveTitle: String = getString(com.starnest.core.R.string.ok),
    positiveCallback: (() -> Unit)? = null,
    negativeTitle: String? = null,
    negativeCallback: (() -> Unit)? = null,
    dismissCallback: (() -> Unit)? = null,
    isDismissClickOutside: Boolean = true,
    maxWidth: Int = -1,
    isDeleteDialog: Boolean = false
) {

    DeleteDialogFragment.newInstance(
        title, message, positiveTitle, negativeTitle,
        callback = object : DeleteDialogFragment.DialogCallback {
            override fun onNegativeClick() {
                negativeCallback?.invoke()
            }

            override fun onPositiveClick() {
                positiveCallback?.invoke()
            }

            override fun onCancel() {
                dismissCallback?.invoke()
            }
        },
        isDismissClickOutside = isDismissClickOutside,
        maxWidth = maxWidth,
        isDeleteDialog = isDeleteDialog
    ).apply {
        showAllowingStateLoss(fragmentManager, "")
    }
}

data class MenuItem(var title: String, var icon: Drawable?) {
}


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
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }
}


fun Context.startVoice(voiceResultLauncher: ActivityResultLauncher<Intent>?) {
    val intent = TextRecognizerUtil.getInstance().getIntent(this)

    try {
        if (intent != null) {
            voiceResultLauncher?.launch(intent)
        } else {
            toast("Ops! Your device doesn't support Speech to Text")
        }
    } catch (ignored: Exception) {
        toast("Ops! Yo  ur device doesn't support Speech to Text")
    }
}


fun Context.showColorPalette(
    defaultColor: Int = Color.BLACK,
    success: (Int) -> Unit,
    cancel: (() -> Unit)? = null
) {
    showMoreColor(defaultColor, success, cancel)
}


val Context.isDarkMode: Boolean
    get() = false

val Context.isPortrait: Boolean
    get() = this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

val Context.isLandscape: Boolean
    get() = this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

val Context.isPhonePortrait: Boolean
    get() = !isTablet && isPortrait

val Context.isPhoneLandscape: Boolean
    get() = !isTablet && isLandscape

val Context.isTabletPortrait: Boolean
    get() = isTablet && isPortrait

val Context.isTabletLandscape: Boolean
    get() = isTablet && isLandscape