package com.starnest.common.extension

import android.os.Build
import android.widget.EditText
import androidx.core.view.WindowInsetsCompat
import com.starnest.core.extension.hideKeyboard
import com.starnest.core.extension.runDelayed
import com.starnest.core.extension.showKeyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


fun EditText.clearFocusAndHideKeyboard() {
    clearFocus()
    hideKeyboard()
}

fun EditText.forceRequestShowKeyboard(delayTime: Long = 200L) {
    MainScope().launch(Dispatchers.Main) {
        delay(delayTime)
        requestFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowInsetsController?.show(WindowInsetsCompat.Type.ime())
        } else {
            showKeyboard()
        }
        trySetSelection()
    }
}

fun EditText.requestShowKeyboard(delayTime: Long = 200L) {
    runDelayed(delayTime) {
        requestFocus()
        showKeyboard()
        trySetSelection()
    }
}

fun EditText.trySetSelection(newSelection: Int? = null) {
    try {
        setSelection(newSelection ?: text.length)
    } catch (_: Exception) {
        setSelection(text.length)
    }
}

/**
 * Gets the selected text from the EditText.
 * @return The selected text if there is a selection, empty string otherwise
 */
fun EditText.getSelectedText(): String {
    val selStart = selectionStart
    val selEnd = selectionEnd
    return if (selStart != -1 && selEnd != -1) {
        text.toString().substring(selStart, selEnd)
    } else {
        ""
    }
}