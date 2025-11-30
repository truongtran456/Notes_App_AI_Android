package com.starnest.common.ui.view.menuview

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.starnest.common.BR
import com.starnest.common.R
import com.starnest.common.databinding.ItemMenuViewBinding
import com.starnest.common.extension.screenSize
import com.starnest.core.extension.px
import com.starnest.core.ui.widget.AbstractView
import dagger.hilt.android.AndroidEntryPoint


interface MenuOptionListener {
    fun onClick(menu: MenuOption)
    fun onCheck(menu: MenuOption, isChecked: Boolean) {}
}

enum class PopupPosition {
    UP, DOWN
}

@AndroidEntryPoint
class MenuView(
    context: Context,
    attrs: AttributeSet?
) : AbstractView(context, attrs) {



    var chatMenus = ArrayList<MenuOption>()
    var listener: MenuOptionListener? = null

    override fun layoutId(): Int = R.layout.item_menu_view

    override fun viewBinding() = binding as ItemMenuViewBinding

    override fun viewInitialized() {
        post {
            setupRecyclerView()
        }
    }

    private fun setupRecyclerView() {
        viewBinding().apply {
            menuRecyclerView.apply {
                adapter = MenuOptionAdapter(object : MenuOptionListener {
                    override fun onClick(menu: MenuOption) {
                        listener?.onClick(menu)
                    }

                    override fun onCheck(menu: MenuOption, isChecked: Boolean) {
                        listener?.onCheck(menu, isChecked)
                    }
                })

                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

                setVariable(BR.menus, chatMenus)
                executePendingBindings()
            }
        }
    }

    companion object {
        fun show(
            context: Context,
            anchor: View,
            menus: ArrayList<MenuOption>,
            listener: MenuOptionListener,
            width: Int? = null
        ) {

            val optionMenuView = MenuView(context, null).apply {
                this.chatMenus = menus
            }

            val popupWindow = PopupWindow(
                optionMenuView,
                width ?: 200.px,
                LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                elevation = context.resources.getDimension(com.starnest.core.R.dimen.dp_16)
                isClippingEnabled = true

                showPopupAtLocation(this, anchor = anchor, contentView = optionMenuView)
            }

            optionMenuView.post {
                showPopupAtLocation(popupWindow, anchor = anchor, contentView = optionMenuView)
            }

            optionMenuView.listener = object : MenuOptionListener {
                override fun onClick(menu: MenuOption) {
                    listener.onClick(menu)
                    popupWindow.dismiss()
                }

                override fun onCheck(menu: MenuOption, isChecked: Boolean) {
                    listener.onCheck(menu, isChecked)
                }
            }
        }

        private fun showPopupAtLocation(popupWindow: PopupWindow, anchor: View, contentView: View) {
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)

            val anchorX = location[0]
            val anchorY = location[1]

            contentView.measure(
                MeasureSpec.UNSPECIFIED,
                MeasureSpec.UNSPECIFIED
            )
            val popupHeight = contentView.measuredHeight
            val popupX = anchorX + anchor.width - popupWindow.width

            val screenHeight = anchor.context.screenSize().height

            val spaceBelow = screenHeight - (anchorY + popupHeight)
            val showAbove = spaceBelow < popupHeight
            val y = if (showAbove) anchorY - popupHeight else anchorY + popupHeight + anchor.height

            popupWindow.showAtLocation(
                anchor,
                Gravity.NO_GRAVITY,
                popupX,
                y
            )
        }
    }
}