package com.starnest.common.extension

import android.content.res.ColorStateList
import android.os.Build
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.core.content.res.ResourcesCompat
import com.starnest.core.extension.detailTextColor
import com.starnest.core.extension.getColorFromAttr
import com.starnest.core.extension.titleTextColor
import com.starnest.resources.R


fun SearchView.configDefault() {
    try {
        findViewById<EditText>(androidx.appcompat.R.id.search_src_text).apply {
            setHintTextColor(context.detailTextColor)
            setTextColor(context.titleTextColor)
            textSize = 14f
            typeface = ResourcesCompat.getFont(context, R.font.nunito_regular)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textCursorDrawable = null
            }
        }
        setIconifiedByDefault(false)
        findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon).apply {
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(context.detailTextColor)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun SearchView.configDefaultLight() {
    try {
        findViewById<EditText>(androidx.appcompat.R.id.search_src_text).apply {
            setHintTextColor(context.getColor(R.color.gray909090))
            setTextColor(context.getColor(com.starnest.core.R.color.black))
            textSize = 14f
            typeface = ResourcesCompat.getFont(context, R.font.nunito_regular)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textCursorDrawable = null
            }
        }
        setIconifiedByDefault(false)
        findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon).apply {
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(context.getColor(R.color.gray909090))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun SearchView.disable() {
    findViewById<EditText>(androidx.appcompat.R.id.search_src_text).apply {
        isEnabled = false
    }
    isEnabled = false
}

fun SearchView.showSearch(callback: () -> Unit) {
    try {
        findViewById<EditText>(androidx.appcompat.R.id.search_src_text).apply {
            setOnFocusChangeListener { _, _ ->
                callback()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun SearchView.hideCloseIcon() {
    try {
        findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn).apply {
            setImageDrawable(null)
            isEnabled = false
        }
        isEnabled = false
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun SearchView.clear() {
    try {
        findViewById<EditText>(androidx.appcompat.R.id.search_src_text).apply {
            setText("")
            clearFocus()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}