package com.starnest.common.ui.view

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import com.starnest.common.R
import com.starnest.common.databinding.ItemAppEmptyDataViewBinding
import com.starnest.core.extension.gone
import com.starnest.core.ui.widget.AbstractView

class AppEmptyDataView(context: Context, attrs: AttributeSet?) : AbstractView(context, attrs) {

    interface OnEmptyDataViewListener {
        fun onStart()
    }

    var listener: OnEmptyDataViewListener? = null

    override fun layoutId(): Int = R.layout.item_app_empty_data_view

    override fun viewBinding() = binding as ItemAppEmptyDataViewBinding

    override fun getStyleableId(): IntArray? = com.starnest.core.R.styleable.EmptyDataView

    private var title: String = ""
    private var buttonTitle: String = ""
    private var emptyIconResId: Int = com.starnest.resources.R.drawable.bg_empty_chat

    override fun viewInitialized() {
        viewBinding().apply {
            tvEmpty.text = title
            tvStart.text = buttonTitle
            tvStart.gone(buttonTitle.isEmpty())
            tvStart.setOnClickListener {
                listener?.onStart()
            }
            ivEmpty.setImageResource(emptyIconResId)
        }
    }

    override fun initDataFromStyleable(a: TypedArray) {
        title = a.getString(com.starnest.core.R.styleable.EmptyDataView_empty_message) ?: ""
        buttonTitle = a.getString(com.starnest.core.R.styleable.EmptyDataView_button_title) ?: ""
        emptyIconResId =
            a.getResourceId(com.starnest.core.R.styleable.EmptyDataView_empty_icon, com.starnest.resources.R.drawable.bg_empty_chat)
    }
}