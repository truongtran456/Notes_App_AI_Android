package com.starnest.common.util

import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.starnest.core.extension.keyboardVisibilityChanges
import com.starnest.core.extension.px
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object KeyboardUtil {

    fun listenerKeyboardVisibleForAndroid15AndAbove(
        activity: FragmentActivity,
        view: View,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            listenerKeyboardVisible(activity = activity, view = view)
        }
    }

    fun listenerKeyboardVisible(
        activity: FragmentActivity,
        view: View,
    ) {
        val rootView = activity.window.decorView

        activity.lifecycleScope.launch(Dispatchers.Main) {
            rootView.keyboardVisibilityChanges().collect { isVisible ->
                val keyboardHeight = if (isVisible) {
                    val windowInsets =
                        ViewCompat.getRootWindowInsets(rootView) ?: return@collect
                    val imeInsets =
                        windowInsets.getInsets(WindowInsetsCompat.Type.ime()) ?: return@collect
                    val navInsets =
                        windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())

                    imeInsets.bottom - navInsets.bottom
                } else {
                    0.px
                }

                view.updatePadding(bottom = keyboardHeight)
            }
        }
    }

    fun onKeyboardShown(
        activity: FragmentActivity,
        onKeyboardShown: () -> Unit,
    ) {
        val rootView = activity.window.decorView

        activity.lifecycleScope.launch(Dispatchers.Main) {
            rootView.keyboardVisibilityChanges().collect { isVisible ->
                if (isVisible) {
                    onKeyboardShown()
                }
            }
        }
    }

}

