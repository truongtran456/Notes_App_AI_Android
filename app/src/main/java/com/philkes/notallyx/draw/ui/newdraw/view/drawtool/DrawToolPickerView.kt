package com.philkes.notallyx.draw.ui.newdraw.view.drawtool

import android.content.Context
import android.os.Build
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.BR
import com.philkes.notallyx.R
import com.philkes.notallyx.common.extension.addSpaceDecoration
import com.philkes.notallyx.common.extension.debounceClick
import com.philkes.notallyx.common.extension.isLandscape
import com.philkes.notallyx.common.extension.isPhoneLandscape
import com.philkes.notallyx.common.extension.isPhonePortrait
import com.philkes.notallyx.common.extension.isTabletLandscape
import com.philkes.notallyx.common.extension.isTabletPortrait
import com.philkes.notallyx.common.extension.px
import com.philkes.notallyx.common.model.DrawToolBrush
import com.philkes.notallyx.common.model.DrawToolPenType
import com.philkes.notallyx.core.ui.widget.AbstractView
import com.philkes.notallyx.databinding.ItemDrawPickerViewBinding
import com.philkes.notallyx.draw.ui.newdraw.view.drawtool.DrawToolData
import com.philkes.notallyx.draw.ui.newdraw.view.drawtoolconfig.DrawToolConfigView
import com.philkes.notallyx.draw.ui.newdraw.view.drawtoolsizepenview.DrawToolSizePenView
import com.philkes.notallyx.draw.ui.newdraw.view.drawtoolsizepenview.DrawToolValueType

class DrawToolPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractView(context, attrs) {

    override fun layoutId(): Int = R.layout.item_draw_picker_view

    override fun viewBinding(): ItemDrawPickerViewBinding =
        binding as ItemDrawPickerViewBinding

    interface OnItemClickListener {
        fun onItemClick(tool: DrawToolBrush)
        fun onSave(tool: DrawToolBrush)
        fun onDelete(tool: DrawToolBrush)
        fun onPaletteClick()
        fun onEyeDropperClick()
        fun onDoneClick() // Callback khi click nút Done
        fun onZoomClick() // Callback khi click nút Zoom
    }

    var listener: OnItemClickListener? = null

    private val drawAdapter by lazy {
        DrawToolPickerAdapter(context, object : DrawToolPickerAdapter.OnItemClickListener {
            override fun onClick(tool: DrawToolBrush) {
                listener?.onItemClick(tool)
                deselectEraser()
            }

            override fun onToolEditDetails(tool: DrawToolBrush) {
                showToolConfigMenu(tool = tool)
            }

            override fun onDelete(tool: DrawToolBrush) {
                listener?.onDelete(tool)
            }
        })
    }

    var tools: ArrayList<DrawToolBrush> = arrayListOf()
        set(value) {
            field = value
            viewBinding().setVariable(BR.tools, value)
            viewBinding().executePendingBindings()
            drawAdapter.updateListItems(value)
        }

    private var isEraserSelected: Boolean = false
        set(value) {
            field = value
            viewBinding().eraserView.isSelected = isEraserSelected
        }

    private var isEditPenModeEnabled: Boolean = false
        set(value) {
            field = value
            setEditPenModeEnabled()
        }

    override fun viewInitialized() {
        post {
            setupRecyclerView()
            setupLayoutManager()
        }
        setupAction()
    }

    private fun setupAction() {
        viewBinding().apply {
            ivDone.setOnClickListener {
                listener?.onDoneClick()
            }
            eraserView.setOnClickListener {
                // Nếu eraser đã được chọn → mở menu chỉnh sửa (click lần 2)
                if (isEraserSelected) {
                    showToolConfigMenu(DrawToolData.eraserPen)
                    return@setOnClickListener
                }
                
                // Click lần 1: chọn eraser
                isEraserSelected = true
                drawAdapter.clearSelection() // Bỏ chọn tất cả pen trong RecyclerView
                listener?.onItemClick(DrawToolData.eraserPen) // Gọi callback để áp dụng eraser vào canvas
            }
            ivPalette.setOnClickListener {
                listener?.onPaletteClick()
            }
            ivEdit.setOnClickListener {
                isEditPenModeEnabled = !isEditPenModeEnabled
            }
            ivSize.setOnClickListener {
                showToolSizePen(it, typeTool = DrawToolValueType.SIZE)
            }
            ivOpacity.setOnClickListener {
                showToolSizePen(it, typeTool = DrawToolValueType.OPACITY)
            }
            ivEyedropper.setOnClickListener {
                listener?.onEyeDropperClick()
            }
            ivZoom.setOnClickListener {
                listener?.onZoomClick()
            }
        }
    }

    private fun showToolConfigMenu(tool: DrawToolBrush) {
        viewBinding().apply {
            DrawToolConfigView.show(
                context = context,
                anchor = ivEdit,
                drawToolBrush = tool,
                height = if (tool.type == DrawToolPenType.ERASER) 340.px else null,
                listener = object : DrawToolConfigView.DrawToolConfigListener {
                    override fun onChange(drawToolBrush: DrawToolBrush) {
                        // Khi thay đổi brush config từ menu edit
                        val brush = drawToolBrush.copy(
                            isAdd = false,
                            isShowDelete = false,
                            isSelected = true // Giữ selected state
                        )
                        
                        // Cập nhật brush trong list
                        val currentTools = this@DrawToolPickerView.tools
                        val index = currentTools.indexOfFirst { it.id == tool.id }
                        if (index >= 0) {
                            currentTools[index] = brush
                            drawAdapter.updateListItems(currentTools)
                        }
                        
                        // Gọi callback để Activity cập nhật canvas
                        listener?.onItemClick(brush)
                        
                        // Lưu nếu đang ở chế độ edit
                        if (isEditPenModeEnabled) {
                            listener?.onSave(brush)
                        }
                    }
                }
            )
        }
    }

    private fun showToolSizePen(anchors: View, typeTool: DrawToolValueType) {
        // Lấy brush đang được chọn (từ adapter hoặc eraser nếu đang chọn eraser)
        val currentBrush = if (isEraserSelected) {
            DrawToolData.eraserPen
        } else {
            tools.firstOrNull { it.isSelected } ?: return
        }
        
        viewBinding().apply {
            DrawToolSizePenView.show(
                context = context,
                anchor = anchors,
                type = typeTool,
                brush = currentBrush,
                initialValue = when (typeTool) {
                    DrawToolValueType.SIZE -> currentBrush.sliderSize
                    DrawToolValueType.OPACITY -> currentBrush.opacity
                    DrawToolValueType.STREAMLINE -> 10f // Giá trị mặc định cho streamline
                },
                onChange = { updatedBrush ->
                    // Khi user thay đổi giá trị → cập nhật brush trong list và canvas ngay lập tức
                    val currentTools = this@DrawToolPickerView.tools
                    val index = currentTools.indexOfFirst { it.id == currentBrush.id }
                    if (index >= 0) {
                        // Cập nhật brush trong list với giá trị mới
                        val updatedBrushWithSelection = updatedBrush.copy(isSelected = true)
                        currentTools[index] = updatedBrushWithSelection
                        drawAdapter.updateListItems(currentTools)
                    }
                    
                    // Gọi callback để Activity cập nhật canvas
                    listener?.onItemClick(updatedBrush.copy(isSelected = true))
                },
                listener = object : DrawToolSizePenView.OnValueChangeListener {
                    override fun onValueChanged(type: DrawToolValueType, value: Float) {
                        // Callback khi slider thay đổi (có thể dùng để update UI khác)
                        when (type) {
                            DrawToolValueType.SIZE -> {
                                // Xử lý khi size thay đổi
                            }
                            DrawToolValueType.OPACITY -> {
                                // Xử lý khi opacity thay đổi
                            }
                            DrawToolValueType.STREAMLINE -> {
                                // Xử lý khi streamline thay đổi
                            }
                        }
                    }
                }
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setEditPenModeEnabled() {
        viewBinding().ivEdit.setImageResource(
            if (isEditPenModeEnabled) R.drawable.ic_check else R.drawable.ic_ruler_cross_pen
        )
        val brushes = tools
        if (isEditPenModeEnabled) {
            if (!tools.any { it.isAdd }) {
                tools.add(0, DrawToolData.addPen)
            }
        } else {
            tools.removeIf { it.isAdd }
        }
        drawAdapter.isEditModeEnabled = isEditPenModeEnabled
        drawAdapter.updateListItems(brushes)
        if (isEditPenModeEnabled) {
            viewBinding().drawToolPickerView.smoothScrollToPosition(0)
        }
    }

    fun deselectEraser() {
        isEraserSelected = false
    }

    fun applyTools(items: ArrayList<DrawToolBrush>) {
        tools = items
    }

    private fun setupRecyclerView() {
        viewBinding().drawToolPickerView.adapter = drawAdapter
        drawAdapter.updateListItems(tools)
    }

    private fun setupLayoutManager() {
        val spanCount = when {
            context.isPhonePortrait -> 7.3
            context.isPhoneLandscape -> 5.3
            context.isTabletPortrait -> 8.3
            context.isTabletLandscape -> 9.0
            else -> 7.3
        }

        val spacing = when {
            context.isPhonePortrait -> resources.getDimension(R.dimen.dp_16).toInt()
            context.isPhoneLandscape -> resources.getDimension(R.dimen.dp_12).toInt()
            context.isTabletPortrait -> resources.getDimension(R.dimen.dp_20).toInt()
            context.isTabletLandscape -> resources.getDimension(R.dimen.dp_16).toInt()
            else -> resources.getDimension(R.dimen.dp_12).toInt()
        }

        viewBinding().drawToolPickerView.apply {
            layoutManager =
                if (context.isLandscape) {
                    object : LinearLayoutManager(context, RecyclerView.VERTICAL, false) {
                        override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
                            val itemHeight = (height - (spanCount.toInt() - 1) * spacing) / spanCount
                            lp.height = itemHeight.toInt()
                            return true
                        }
                    }
                } else {
                    object : LinearLayoutManager(context, RecyclerView.HORIZONTAL, false) {
                        override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
                            val itemWidth = (width - (spanCount.toInt() - 1) * spacing) / spanCount
                            lp.width = itemWidth.toInt()
                            return true
                        }
                    }
                }
            addSpaceDecoration(spacing, false)
        }
    }
}

