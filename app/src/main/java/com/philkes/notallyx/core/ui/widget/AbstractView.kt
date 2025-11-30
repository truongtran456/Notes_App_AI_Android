package com.philkes.notallyx.core.ui.widget

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding

abstract class AbstractView(context: Context, attrs: AttributeSet?): ConstraintLayout(context, attrs) {
    lateinit var binding: ViewDataBinding

    val isViewInitialized: Boolean
        get() {
            return this::binding.isInitialized
        }

    abstract fun layoutId(): Int

    init {
        initialize(attrs)
    }

    fun initialize(attrs: AttributeSet?) {
        attrs?.let {
            initAttr(it)
        }
        initLayout()
    }

    abstract fun viewInitialized()

    private fun initLayout() {
        binding = DataBindingUtil.inflate(LayoutInflater.from(context), layoutId(), this, true)
        viewInitialized()
    }

    open fun viewBinding(): ViewDataBinding = binding

    open fun getStyleableId(): IntArray? {
        return null
    }

    open fun initDataFromStyleable(a: TypedArray) {}

    private fun initAttr(attr: AttributeSet) {
        getStyleableId()?.let {
            val a = context.theme.obtainStyledAttributes(attr, it, 0, 0)
            try {
                initDataFromStyleable(a)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                a.recycle()
            }
        }
    }
}

