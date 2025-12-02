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
    private var canvasBackgroundColor: Int = Color.WHITE
    
    // Lưu strokes để persist drawing
    private val savedStrokes = mutableListOf<DrawingStroke>()
    
    // Bitmap để lưu drawing state (backup cho eyedropper)
    private var savedDrawingBitmap: Bitmap? = null
    private var dividerY: Float = 0f // Vị trí đường divider
    
    // Bitmaps để xử lý eraser (theo flow Starnest)
    private var layerBitmap: Bitmap? = null // Chứa tất cả strokes đã vẽ (đã được update với eraser)
    private var strokeBitmap: Bitmap? = null // Bitmap tạm để vẽ stroke mới (với eraser)
    private var resultBitmap: Bitmap? = null // Bitmap kết quả (để hiển thị)
    private var hasEraserBeenUsed: Boolean = false // Flag để đánh dấu đã dùng eraser (layerBitmap đã bị modify)
    
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

    /**
     * Thay đổi màu nền cho vùng canvas (dưới các strokes)
     */
    fun setCanvasBackgroundColor(color: Int) {
        canvasBackgroundColor = color
        invalidate()
    }

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
                    // Sử dụng DST_OUT mode để xóa (theo flow Starnest)
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    // Màu không quan trọng với eraser, nhưng vẫn set để tránh lỗi
                    paint.color = Color.BLACK // DST_OUT cần màu đen
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
                    
                    // Xử lý eraser: copy layerBitmap vào strokeBitmap
                    if (currentBrush?.brush == Brush.HardEraser || currentBrush?.brush == Brush.SoftEraser) {
                        startDrawingWithEraser()
                    }
                    
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
                    
                    // Nếu đang vẽ với eraser, update strokeBitmap ngay lập tức để hiển thị real-time
                    if (currentBrush?.brush == Brush.HardEraser || currentBrush?.brush == Brush.SoftEraser) {
                        strokeBitmap?.let { stroke ->
                            val eraserPaint = Paint(paint).apply {
                                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                                color = Color.BLACK
                                alpha = 255
                            }
                            // Vẽ eraser path lên strokeBitmap (đã copy từ layerBitmap)
                            Canvas(stroke).drawPath(currentPath!!.path, eraserPaint)
                        }
                    }
                    
                    // Mở rộng canvas nếu cần
                    expandCanvasIfNeeded(y)
                    
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Kết thúc vẽ và lưu stroke
                    if (currentPath != null && currentBrush != null) {
                        // Xử lý eraser đặc biệt
                        if (currentBrush!!.brush == Brush.HardEraser || currentBrush!!.brush == Brush.SoftEraser) {
                            endDrawingWithEraser()
                        } else {
                            saveCurrentStroke()
                        }
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
        
        // Vẽ nền canvas (có thể thay đổi bằng nút Background)
        canvas.drawColor(canvasBackgroundColor)
        
        // Apply zoom and pan transformations (chỉ khi zoom mode bật và có scale)
        if (isZoomModeEnabled && scaleFactor != 1.0f) {
            canvas.save()
            canvas.translate(translateX, translateY)
            canvas.scale(scaleFactor, scaleFactor, width / 2f, height / 2f)
        }
        
        // Đảm bảo bitmaps được khởi tạo
        ensureBitmapsInitialized()
        
        // Nếu đang vẽ với eraser, vẽ từ strokeBitmap (đã xóa một phần)
        val isDrawingEraser = currentBrush?.brush == Brush.HardEraser || currentBrush?.brush == Brush.SoftEraser
        if (isDrawingEraser && strokeBitmap != null) {
            // Vẽ strokeBitmap (đang vẽ với eraser) - đã copy từ layerBitmap và xóa một phần
            strokeBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        } else {
            // Vẽ bình thường: vẽ từ layerBitmap hoặc từ strokes
            if (layerBitmap != null) {
                // Vẽ từ layerBitmap (đã được update với eraser)
                canvas.drawBitmap(layerBitmap!!, 0f, 0f, null)
            } else {
                // Fallback: vẽ từ strokes (khi chưa có layerBitmap)
                savedStrokes.forEach { stroke ->
                    if (stroke.brush != Brush.HardEraser && stroke.brush != Brush.SoftEraser) {
                        val path = DrawingStroke.stringToPath(stroke.pathData)
                        val paint = createPaintFromStroke(stroke)
                        canvas.drawPath(path, paint)
                    }
                }
            }
            
            // Vẽ các paths hiện tại (đang vẽ - không phải eraser)
            paths.forEach { drawingPath ->
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
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    color = Color.BLACK
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
        layerBitmap?.recycle()
        layerBitmap = null
        strokeBitmap?.recycle()
        strokeBitmap = null
        resultBitmap?.recycle()
        resultBitmap = null
        hasEraserBeenUsed = false
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
     * Bắt đầu vẽ với eraser: copy layerBitmap vào strokeBitmap
     */
    private fun startDrawingWithEraser() {
        ensureBitmapsInitialized()
        
        layerBitmap?.let { layer ->
            strokeBitmap?.let { stroke ->
                // Xóa strokeBitmap
                stroke.eraseColor(Color.TRANSPARENT)
                
                // Copy layerBitmap vào strokeBitmap (để có nền để xóa)
                Canvas(stroke).drawBitmap(layer, 0f, 0f, null)
            }
        }
    }
    
    /**
     * Kết thúc vẽ với eraser: xử lý xóa trên layerBitmap
     */
    private fun endDrawingWithEraser() {
        val path = currentPath?.path ?: return
        
        ensureBitmapsInitialized()
        
        // strokeBitmap đã được update trong ACTION_MOVE với eraser path
        // Giờ chỉ cần copy strokeBitmap (đã xóa) vào layerBitmap
        strokeBitmap?.let { stroke ->
            // QUAN TRỌNG: Thay thế hoàn toàn layerBitmap bằng strokeBitmap (đã xóa)
            // Đây là kết quả cuối cùng sau khi xóa - layerBitmap giờ chứa drawing đã bị xóa
            layerBitmap?.let { layer ->
                // Xóa layerBitmap cũ và vẽ lại từ strokeBitmap (đã xóa)
                layer.eraseColor(Color.TRANSPARENT)
                val layerCanvas = Canvas(layer)
                layerCanvas.drawColor(Color.WHITE) // Vẽ nền trắng
                layerCanvas.drawBitmap(stroke, 0f, 0f, null) // Vẽ strokeBitmap (đã xóa)
            }
            
            // Đánh dấu đã dùng eraser (layerBitmap đã bị modify)
            hasEraserBeenUsed = true
        }
        
        // Lưu eraser stroke để có thể undo (tùy chọn)
        // Không lưu eraser stroke vào savedStrokes vì nó chỉ là thao tác xóa
        
        // Clear path hiện tại
        paths.remove(currentPath)
        currentPath = null
        
        invalidate()
    }
    
    /**
     * Đảm bảo các bitmaps được khởi tạo
     */
    private fun ensureBitmapsInitialized() {
        if (width <= 0 || height <= 0) return
        
        val needRebuildLayer = layerBitmap == null || 
                               layerBitmap!!.width != width || 
                               layerBitmap!!.height != height
        
        if (needRebuildLayer) {
            layerBitmap?.recycle()
            layerBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val layerCanvas = Canvas(layerBitmap!!)
            layerCanvas.drawColor(Color.WHITE)
            
            // Vẽ tất cả saved strokes lên layerBitmap (trừ eraser strokes)
            savedStrokes.forEach { stroke ->
                if (stroke.brush != Brush.HardEraser && stroke.brush != Brush.SoftEraser) {
                    val path = DrawingStroke.stringToPath(stroke.pathData)
                    val paint = createPaintFromStroke(stroke)
                    layerCanvas.drawPath(path, paint)
                }
            }
            
            // Reset flag khi rebuild (vì đã rebuild từ savedStrokes)
            hasEraserBeenUsed = false
        }
        // QUAN TRỌNG: Nếu layerBitmap đã tồn tại và size không đổi, KHÔNG rebuild từ savedStrokes
        // Vì layerBitmap đã chứa kết quả sau khi tẩy, nếu rebuild sẽ mất phần đã tẩy
        
        if (strokeBitmap == null || strokeBitmap!!.width != width || strokeBitmap!!.height != height) {
            strokeBitmap?.recycle()
            strokeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            strokeBitmap!!.eraseColor(Color.TRANSPARENT)
        }
        
        if (resultBitmap == null || resultBitmap!!.width != width || resultBitmap!!.height != height) {
            resultBitmap?.recycle()
            resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            resultBitmap!!.eraseColor(Color.TRANSPARENT)
        }
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
        
        // Update layerBitmap với stroke mới
        ensureBitmapsInitialized()
        layerBitmap?.let { layer ->
            val pathObj = DrawingStroke.stringToPath(stroke.pathData)
            val paintObj = createPaintFromStroke(stroke)
            Canvas(layer).drawPath(pathObj, paintObj)
        }
        
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
        
        // Reset bitmaps để rebuild từ strokes mới
        layerBitmap?.recycle()
        layerBitmap = null
        strokeBitmap?.recycle()
        strokeBitmap = null
        resultBitmap?.recycle()
        resultBitmap = null
        hasEraserBeenUsed = false
        
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

