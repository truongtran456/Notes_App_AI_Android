package com.philkes.notallyx.presentation.view.note.ai

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ItemAiToolbarMenuPopupViewBinding

class AIToolBarMenuPopupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding: ItemAiToolbarMenuPopupViewBinding

    init {
        val inflater = android.view.LayoutInflater.from(context)
        binding = ItemAiToolbarMenuPopupViewBinding.inflate(inflater, this, true)
    }

    interface OnItemClickListener {
        fun onClick(option: AIOption)
    }

    var aiOptions = ArrayList<AIOption>()
    var listener: OnItemClickListener? = null

    fun setupRecyclerView() {
        val spacing = resources.getDimensionPixelSize(R.dimen.dp_4) // Spacing 4dp giữa các items

        binding.menuRecyclerView.apply {
            adapter = AIOptionsAdapter(context, object : AIOptionsAdapter.OnItemClickListener {
                override fun onItemClick(option: AIOption) {
                    listener?.onClick(option)
                }
            }).apply {
                updateList(aiOptions)
            }
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    val position = parent.getChildAdapterPosition(view)
                    if (position != RecyclerView.NO_POSITION && position < parent.adapter!!.itemCount - 1) {
                        outRect.right = spacing
                    }
                }
            })
        }
    }

    companion object {
        /**
         * Hàm static để hiển thị popup menu
         * @param context Context của activity
         * @param anchor View mà popup sẽ hiển thị bên dưới (nút AI)
         * @param options Danh sách các option
         * @param listener Callback khi click vào option
         */
        fun show(
            context: Context,
            anchor: View,
            options: ArrayList<AIOption>,
            listener: OnItemClickListener
        ) {
            try {
                android.util.Log.d("AIToolBarMenuPopupView", "show() called with ${options.size} options")
                
                // Tạo instance của popup view
                val popupView = AIToolBarMenuPopupView(context, null).apply {
                    this.aiOptions = options
                    setupRecyclerView()
                }

                // Measure view trước
                popupView.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                
                android.util.Log.d("AIToolBarMenuPopupView", "PopupView measured: ${popupView.measuredWidth}x${popupView.measuredHeight}")

                // Tạo PopupWindow với kích thước cụ thể
                val popupWindow = PopupWindow(
                    popupView,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
                ).apply {
                    elevation = context.resources.getDimension(R.dimen.dp_16)
                    isClippingEnabled = false
                    setBackgroundDrawable(
                        android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                    )
                    isOutsideTouchable = true
                    isFocusable = true
                }
                
                // Animation: scale + fade
                popupView.scaleX = 0.8f
                popupView.scaleY = 0.8f
                popupView.alpha = 0f

                // Set listener cho popup view
                popupView.listener = object : OnItemClickListener {
                    override fun onClick(option: AIOption) {
                        android.util.Log.d("AIToolBarMenuPopupView", "Option clicked: ${option.type}")
                        listener.onClick(option)
                        popupWindow.dismiss()  // Đóng popup sau khi click
                    }
                }

                // Hiển thị popup sau khi view đã được measure
                anchor.post {
                    try {
                        android.util.Log.d("AIToolBarMenuPopupView", "Attempting to show popup")
                        showPopupAtLocation(popupWindow, anchor, popupView)
                        
                        // Đợi một chút để popup render xong, rồi mới animate
                        popupView.postDelayed({
                            // Start animation sau khi popup hiển thị
                            popupView.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .alpha(1f)
                                .setDuration(200)
                                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                                .start()
                        }, 50)
                        
                        android.util.Log.d("AIToolBarMenuPopupView", "Popup shown successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("AIToolBarMenuPopupView", "Error showing popup", e)
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AIToolBarMenuPopupView", "Error creating popup", e)
                e.printStackTrace()
            }
        }

        /**
         * Tính toán vị trí và hiển thị popup
         */
        private fun showPopupAtLocation(
            popupWindow: PopupWindow,
            anchor: View,
            contentView: View
        ) {
            try {
                val location = IntArray(2)
                anchor.getLocationOnScreen(location)  // Lấy vị trí của anchor view trên màn hình

                val anchorX = location[0]  // Tọa độ X của anchor
                val anchorY = location[1]  // Tọa độ Y của anchor
                val anchorWidth = anchor.width
                val anchorHeight = anchor.height

                // Measure popup window để lấy width
                contentView.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val popupWidth = contentView.measuredWidth
                val popupHeight = contentView.measuredHeight

                // Margin từ nút AI: 10dp
                val marginY = contentView.resources.getDimensionPixelSize(R.dimen.dp_10)
                
                // Lấy kích thước màn hình
                val displayMetrics = contentView.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val marginX = contentView.resources.getDimensionPixelSize(R.dimen.dp_16)

                // Tính toán vị trí mũi nhọn: căn giữa với nút AI
                val anchorCenterX = anchorX + (anchorWidth / 2)
                val arrowWidthPx = contentView.resources.getDimensionPixelSize(R.dimen.dp_20).toFloat()
                val arrowHeightPx = contentView.resources.getDimensionPixelSize(R.dimen.dp_8).toFloat()
                
                // Calculate initial X position: centered with anchor
                var finalX = anchorCenterX - (popupWidth / 2)

                // Adjust if it goes off screen right
                if (finalX + popupWidth > screenWidth - marginX) {
                    finalX = screenWidth - popupWidth - marginX
                }
                // Adjust if it goes off screen left
                if (finalX < marginX) {
                    finalX = marginX
                }
                
                // Tính toán vị trí Y: bên dưới anchor với margin 10dp
                val finalY = anchorY + anchorHeight + marginY

                // Tính toán vị trí X của mũi nhọn trong popup (tính từ trái popup)
                val arrowXInPopup = (anchorCenterX - finalX).toFloat()
                
                // Tạo custom drawable với mũi nhọn
                val cornerRadius = contentView.resources.getDimension(R.dimen.dp_16)
                val bubbleDrawable = BubbleDrawable(
                    cornerRadius = cornerRadius,
                    arrowWidth = arrowWidthPx,
                    arrowHeight = arrowHeightPx,
                    arrowX = arrowXInPopup.coerceIn(arrowWidthPx / 2f, (popupWidth - arrowWidthPx / 2f).toFloat())
                )
                
                // Set background cho popup container TRƯỚC KHI hiển thị
                val popupContainer = contentView.findViewById<View>(R.id.popupContainer)
                popupContainer?.background = bubbleDrawable

                // Hiển thị popup tại vị trí đã tính toán
                // Sử dụng root view của activity thay vì anchor
                val rootView = anchor.rootView
                popupWindow.showAtLocation(rootView, Gravity.NO_GRAVITY, finalX, finalY)
                
                android.util.Log.d("AIToolBarMenuPopupView", "Bubble created - anchorCenter: $anchorCenterX, popupLeft: $finalX, arrowX: ${arrowXInPopup.coerceIn(arrowWidthPx / 2f, (popupWidth - arrowWidthPx / 2f).toFloat())}")
            } catch (e: Exception) {
                android.util.Log.e("AIToolBarMenuPopupView", "Error in showPopupAtLocation", e)
            }
        }
    }
}

