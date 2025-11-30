package com.philkes.notallyx.draw.ui.newdraw.view.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.philkes.notallyx.R
import com.philkes.notallyx.common.model.Brush
import com.philkes.notallyx.common.model.DrawToolBrush
import com.philkes.notallyx.common.extension.rawColor
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.GestureDetector
import android.view.ScaleGestureDetector
import java.util.UUID
import android.graphics.RectF

/**
 * Simple canvas view for drawing with selected brush tools
 */
class DrawingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    init {
        // Đảm bảo view có thể nhận touch events
        isClickable = true
        isFocusable = true
    }

    private val paths = mutableListOf<DrawingPath>()
    private var currentPath: DrawingPath? = null
    private var currentBrush: DrawToolBrush? = null
    
    // Lưu strokes để persist drawing
    private val savedStrokes = mutableListOf<DrawingStroke>()
    
    // Bitmap để lưu drawing state (backup cho eyedropper)
    private var savedDrawingBitmap: Bitmap? = null
    private var dividerY: Float = 0f // Vị trí đường divider
    
    // Eyedropper mode
    private var isEyeDropperMode: Boolean = false
    private var onColorPickedListener: ((Int) -> Unit)? = null
    
    // Zoom mode
    private var isZoomModeEnabled: Boolean = false
    private var scaleFactor: Float = 1.0f
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var translateX: Float = 0f
    private var translateY: Float = 0f
    
    // Bitmap để lưu canvas content cho eyedropper
    private var canvasBitmap: Bitmap? = null

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (isZoomModeEnabled) {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 5.0f) // Giới hạn zoom từ 0.5x đến 5x
                invalidate()
                return true
            }
            return false
        }
    })

    data class DrawingPath(
        val path: Path,
        val paint: Paint
    )

    fun setBrush(brush: DrawToolBrush) {
        currentBrush = brush
        updatePaint()
    }

    private fun updatePaint() {
        currentBrush?.let { brush ->
            // Xử lý màu
            try {
                paint.color = Color.parseColor(brush.color)
            } catch (e: IllegalArgumentException) {
                paint.color = Color.BLACK // Fallback nếu màu không hợp lệ
            }
            
            // Xử lý kích thước (sliderSize có thể từ 0-100, convert sang pixel)
            paint.strokeWidth = brush.sliderSize.coerceIn(1f, 100f)
            
            // Xử lý opacity (0-1 → 0-255)
            paint.alpha = ((brush.opacity.coerceIn(0f, 1f)) * 255).toInt()
            
            // Xử lý brush type (Pen, Pencil, AirBrush, etc.)
            when (brush.brush) {
                Brush.Pen, Brush.Pencil, Brush.Calligraphy, Brush.FountainPen -> {
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.style = Paint.Style.STROKE
                }
                Brush.Marker, Brush.AirBrush -> {
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.style = Paint.Style.STROKE
                    // AirBrush và Marker có thể cần blur effect (tùy chọn)
                }
                Brush.DashLine -> {
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.style = Paint.Style.STROKE
                    // DashLine cần path effect (có thể thêm sau)
                }
                Brush.NeonLine -> {
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.style = Paint.Style.STROKE
                    // NeonLine có thể cần glow effect (tùy chọn)
                }
                Brush.HardEraser, Brush.SoftEraser -> {
                    // Eraser: xóa thay vì vẽ màu
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.style = Paint.Style.STROKE
                    // Sử dụng CLEAR mode để xóa
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    // Màu không quan trọng với eraser, nhưng vẫn set để tránh lỗi
                    paint.color = Color.TRANSPARENT
                    paint.alpha = 255 // Eraser cần alpha = 255 để xóa đúng
                }
                else -> {
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.style = Paint.Style.STROKE
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Xử lý eyedropper mode (ưu tiên cao nhất)
        if (isEyeDropperMode) {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                    val color = getColorAt(event.x.toInt(), event.y.toInt())
                    onColorPickedListener?.invoke(color)
                    setEyeDropperMode(false)
                    return true
                }
            }
            return true
        }
        
        // Xử lý zoom mode (chỉ khi zoom mode bật)
        if (isZoomModeEnabled) {
            // Xử lý zoom gesture (khi có 2 ngón tay trở lên)
            if (event.pointerCount >= 2) {
                scaleGestureDetector.onTouchEvent(event)
                return true
            }
            
            // Xử lý pan khi zoom mode bật và chỉ có 1 ngón tay
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    // Pan khi zoom
                    translateX += event.x - lastTouchX
                    translateY += event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
            }
            return true
        }
        
        // Xử lý vẽ bình thường (chỉ khi zoom mode tắt và có brush được chọn)
        if (!isZoomModeEnabled && !isEyeDropperMode && currentBrush != null) {
            // Kiểm tra xem có vẽ dưới divider không (nếu có divider)
            val canDraw = dividerY <= 0f || event.y >= dividerY
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Chỉ cho phép vẽ dưới divider
                    if (!canDraw) {
                        return false
                    }
                    
                    // Bắt đầu vẽ với brush config đã set
                    val newPath = Path()
                    newPath.moveTo(event.x, event.y)
                    currentPath = DrawingPath(newPath, Paint(paint))
                    paths.add(currentPath!!)
                    
                    // Mở rộng canvas nếu cần
                    expandCanvasIfNeeded(event.y)
                    
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Chỉ cho phép vẽ dưới divider
                    if (!canDraw || currentPath == null) {
                        return false
                    }
                    
                    // Vẽ tiếp path - sử dụng lineTo để vẽ đơn giản và chính xác
                    val x = event.x
                    val y = event.y
                    currentPath!!.path.lineTo(x, y)
                    
                    // Mở rộng canvas nếu cần
                    expandCanvasIfNeeded(y)
                    
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Kết thúc vẽ và lưu stroke
                    if (currentPath != null && currentBrush != null) {
                        saveCurrentStroke()
                    }
                    currentPath = null
                    return true
                }
            }
        }
        
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Vẽ nền trắng
        canvas.drawColor(Color.WHITE)
        
        // Apply zoom and pan transformations (chỉ khi zoom mode bật và có scale)
        if (isZoomModeEnabled && scaleFactor != 1.0f) {
            canvas.save()
            canvas.translate(translateX, translateY)
            canvas.scale(scaleFactor, scaleFactor, width / 2f, height / 2f)
        }
        
        // Vẽ tất cả saved strokes trước (restore drawing)
        savedStrokes.forEach { stroke ->
            val path = DrawingStroke.stringToPath(stroke.pathData)
            val paint = createPaintFromStroke(stroke)
            
            // Với eraser, cần vẽ trên layer riêng
            if (stroke.brush == Brush.HardEraser || stroke.brush == Brush.SoftEraser) {
                canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
                canvas.drawPath(path, paint)
                canvas.restore()
            } else {
                canvas.drawPath(path, paint)
            }
        }
        
        // Vẽ các paths hiện tại (đang vẽ)
        paths.forEach { drawingPath ->
            val brush = currentBrush
            if (brush != null && (brush.brush == Brush.HardEraser || brush.brush == Brush.SoftEraser)) {
                // Với eraser, cần vẽ trên layer riêng
                canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
                canvas.drawPath(drawingPath.path, drawingPath.paint)
                canvas.restore()
            } else {
                canvas.drawPath(drawingPath.path, drawingPath.paint)
            }
        }
        
        // Update bitmap for eyedropper
        updateCanvasBitmap()
        
        // Vẽ đường divider nếu có
        if (dividerY > 0f) {
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#E0E0E0")
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }
            canvas.drawLine(0f, dividerY, width.toFloat(), dividerY, dividerPaint)
        }
        
        if (isZoomModeEnabled && scaleFactor != 1.0f) {
            canvas.restore()
        }
    }
    
    /**
     * Tạo Paint từ DrawingStroke để vẽ lại
     */
    private fun createPaintFromStroke(stroke: DrawingStroke): Paint {
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            
            // Set màu
            try {
                color = Color.parseColor(stroke.color)
            } catch (e: Exception) {
                color = Color.BLACK
            }
            
            // Set size
            strokeWidth = stroke.size.coerceIn(1f, 100f)
            
            // Set opacity
            alpha = ((stroke.opacity.coerceIn(0f, 1f)) * 255).toInt()
            
            // Set brush type
            when (stroke.brush) {
                Brush.HardEraser, Brush.SoftEraser -> {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    color = Color.TRANSPARENT
                    alpha = 255
                }
                else -> {
                    xfermode = null
                }
            }
        }
        return paint
    }
    
    /**
     * Cập nhật bitmap từ canvas để dùng cho eyedropper
     */
    private fun updateCanvasBitmap() {
        if (width <= 0 || height <= 0) return
        
        // Tạo hoặc cập nhật bitmap
        if (canvasBitmap == null || canvasBitmap!!.width != width || canvasBitmap!!.height != height) {
            canvasBitmap?.recycle()
            canvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        
        val bitmapCanvas = Canvas(canvasBitmap!!)
        bitmapCanvas.drawColor(Color.WHITE)
        
        // Vẽ saved strokes
        savedStrokes.forEach { stroke ->
            val path = DrawingStroke.stringToPath(stroke.pathData)
            val paint = createPaintFromStroke(stroke)
            bitmapCanvas.drawPath(path, paint)
        }
        
        // Vẽ paths hiện tại
        paths.forEach { drawingPath ->
            bitmapCanvas.drawPath(drawingPath.path, drawingPath.paint)
        }
    }
    
    /**
     * Lấy màu tại vị trí (x, y) trên canvas
     */
    fun getColorAt(x: Int, y: Int): Int {
        // Nếu có bitmap, lấy màu từ bitmap
        canvasBitmap?.let { bitmap ->
            if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
                return bitmap.getPixel(x, y)
            }
        }
        
        // Nếu không có bitmap, tìm màu từ paths gần nhất
        // Hoặc trả về màu mặc định (đen)
        return Color.BLACK
    }
    
    /**
     * Bật/tắt chế độ eyedropper
     */
    fun setEyeDropperMode(enabled: Boolean) {
        isEyeDropperMode = enabled
        if (enabled) {
            // Đảm bảo bitmap được tạo
            updateCanvasBitmap()
        }
        invalidate()
    }
    
    /**
     * Set listener để nhận màu khi pick bằng eyedropper
     */
    fun setOnColorPickedListener(listener: (Int) -> Unit) {
        onColorPickedListener = listener
    }
    
    /**
     * Bật/tắt chế độ zoom
     */
    fun setZoomModeEnabled(enabled: Boolean) {
        isZoomModeEnabled = enabled
        if (!enabled) {
            // Reset zoom khi tắt
            scaleFactor = 1.0f
            translateX = 0f
            translateY = 0f
        }
        invalidate()
    }
    
    /**
     * Kiểm tra xem zoom mode có đang bật không
     */
    fun isZoomModeEnabled(): Boolean = isZoomModeEnabled

    fun clear() {
        paths.clear()
        savedStrokes.clear()
        savedDrawingBitmap?.recycle()
        savedDrawingBitmap = null
        invalidate()
    }

    fun undo() {
        if (paths.isNotEmpty()) {
            paths.removeAt(paths.size - 1)
            invalidate()
        } else if (savedStrokes.isNotEmpty()) {
            savedStrokes.removeAt(savedStrokes.size - 1)
            invalidate()
        }
    }
    
    /**
     * Set vị trí đường divider (phân phần có thể vẽ và không thể vẽ)
     */
    fun setDividerY(y: Float) {
        dividerY = y
        invalidate()
    }
    
    /**
     * Mở rộng canvas nếu cần khi vẽ
     */
    private fun expandCanvasIfNeeded(y: Float) {
        val currentHeight = height
        val minHeight = (y + 200).toInt() // Thêm 200px padding để đảm bảo có đủ không gian
        
        if (minHeight > currentHeight) {
            // Request layout để mở rộng canvas
            minimumHeight = minHeight
            requestLayout()
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        
        // Đảm bảo canvas có thể mở rộng không giới hạn
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = measuredHeight.coerceAtLeast(minimumHeight)
        
        setMeasuredDimension(width, height)
    }
    
    /**
     * Lưu stroke hiện tại vào danh sách savedStrokes
     */
    private fun saveCurrentStroke() {
        val path = currentPath?.path ?: return
        val brush = currentBrush ?: return
        
        // Tính boundary của path
        val bounds = RectF()
        path.computeBounds(bounds, true)
        
        // Tạo DrawingStroke
        val stroke = DrawingStroke(
            id = UUID.randomUUID().toString(),
            pathData = DrawingStroke.pathToString(path),
            brush = brush.brush,
            color = brush.color,
            size = brush.sliderSize,
            opacity = brush.opacity,
            rectLeft = bounds.left,
            rectTop = bounds.top,
            rectRight = bounds.right,
            rectBottom = bounds.bottom
        )
        
        // Lưu stroke
        savedStrokes.add(stroke)
        
        // Clear path hiện tại sau khi đã lưu
        paths.remove(currentPath)
        currentPath = null
    }
    
    /**
     * Lấy tất cả strokes đã vẽ (để lưu)
     */
    fun getStrokes(): List<DrawingStroke> {
        return ArrayList(savedStrokes)
    }
    
    /**
     * Load và vẽ lại strokes (restore drawing)
     */
    fun loadStrokes(strokes: List<DrawingStroke>) {
        savedStrokes.clear()
        savedStrokes.addAll(strokes)
        paths.clear()
        invalidate()
    }
    
    /**
     * Lấy bitmap đã vẽ (để lưu hoặc export)
     */
    fun getDrawingBitmap(): Bitmap? {
        if (width <= 0 || height <= 0) {
            // Nếu chưa có kích thước, tạo bitmap tối thiểu
            val minWidth = 100
            val minHeight = 100
            val bitmap = Bitmap.createBitmap(minWidth, minHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            
            // Vẽ strokes
            savedStrokes.forEach { stroke ->
                val path = DrawingStroke.stringToPath(stroke.pathData)
                val paint = createPaintFromStroke(stroke)
                canvas.drawPath(path, paint)
            }
            
            return bitmap
        }
        
        // Tạo bitmap với tất cả drawing
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Vẽ nền trắng
        canvas.drawColor(Color.WHITE)
        
        // Vẽ saved strokes
        savedStrokes.forEach { stroke ->
            val path = DrawingStroke.stringToPath(stroke.pathData)
            val paint = createPaintFromStroke(stroke)
            canvas.drawPath(path, paint)
        }
        
        // Vẽ paths hiện tại
        paths.forEach { drawingPath ->
            canvas.drawPath(drawingPath.path, drawingPath.paint)
        }
        
        return bitmap
    }
    
    /**
     * Restore drawing từ bitmap (backward compatibility)
     */
    fun restoreDrawing(bitmap: Bitmap?) {
        savedDrawingBitmap?.recycle()
        savedDrawingBitmap = bitmap
        paths.clear()
        invalidate()
    }
}

