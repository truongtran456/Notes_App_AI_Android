package com.philkes.notallyx.draw.ui.newdraw.view.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.philkes.notallyx.common.model.Brush
import com.philkes.notallyx.common.model.DrawToolBrush
import java.util.UUID

/** Simple canvas view for drawing with selected brush tools */
class DrawingCanvasView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    View(context, attrs, defStyleAttr) {

    init {
        // ??m b?o view có th? nh?n touch events
        isClickable = true
        isFocusable = true
    }

    private val paths = mutableListOf<DrawingPath>()
    private var currentPath: DrawingPath? = null
    private var currentBrush: DrawToolBrush? = null
    private var canvasBackgroundColor: Int = Color.WHITE
    private var backgroundBitmap: Bitmap? = null
    private var backgroundDrawableResId: Int? = null

    // L?u strokes ?? persist drawing
    private val savedStrokes = mutableListOf<DrawingStroke>()

    // Bitmap ?? l?u drawing state (backup cho eyedropper)
    private var savedDrawingBitmap: Bitmap? = null
    private var dividerY: Float = 0f // V? trí ???ng divider

    // Bitmaps ?? x? lý eraser (theo flow Starnest)
    private var layerBitmap: Bitmap? = null // Ch?a t?t c? strokes ?ã v? (?ã ???c update v?i eraser)
    private var strokeBitmap: Bitmap? = null // Bitmap t?m ?? v? stroke m?i (v?i eraser)
    private var resultBitmap: Bitmap? = null // Bitmap k?t qu? (?? hi?n th?)
    private var hasEraserBeenUsed: Boolean =
        false // Flag ?? ?ánh d?u ?ã dùng eraser (layerBitmap ?ã b? modify)

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

    // Bitmap ?? l?u canvas content cho eyedropper
    private var canvasBitmap: Bitmap? = null

    private val paint =
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

    private val scaleGestureDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    if (isZoomModeEnabled) {
                        scaleFactor *= detector.scaleFactor
                        scaleFactor =
                            scaleFactor.coerceIn(0.5f, 5.0f) // Gi?i h?n zoom t? 0.5x ??n 5x
                        invalidate()
                        return true
                    }
                    return false
                }
            },
        )

    data class DrawingPath(val path: Path, val paint: Paint)

    /** Thay ??i màu n?n cho vùng canvas (d??i các strokes) */
    fun setCanvasBackgroundColor(color: Int) {
        canvasBackgroundColor = color
        backgroundBitmap = null
        backgroundDrawableResId = null
        invalidate()
    }

    /** Set background b?ng drawable (?nh). N?u truy?n null s? quay l?i dùng màu. */
    fun setCanvasBackgroundDrawable(@DrawableRes resId: Int?) {
        backgroundDrawableResId = resId
        if (resId == null) {
            backgroundBitmap = null
            invalidate()
            return
        }
        backgroundBitmap = decodeBackgroundBitmap(resId)
        invalidate()
    }

    private fun decodeBackgroundBitmap(@DrawableRes resId: Int): Bitmap? {
        return try {
            ContextCompat.getDrawable(context, resId)?.toBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun setBrush(brush: DrawToolBrush?) {
        currentBrush = brush
        if (brush != null) {
            updatePaint()
        } else {
            // Clear brush - không th? v? ???c n?a
            currentPath = null
            paths.clear()
        }
    }

    private fun updatePaint() {
        currentBrush?.let { brush ->
            // X? lý màu
            try {
                paint.color = Color.parseColor(brush.color)
            } catch (e: IllegalArgumentException) {
                paint.color = Color.BLACK // Fallback n?u màu không h?p l?
            }

            // X? lý kích th??c (sliderSize có th? t? 0-100, convert sang pixel)
            paint.strokeWidth = brush.sliderSize.coerceIn(1f, 100f)

            // X? lý opacity (0-1 ? 0-255)
            paint.alpha = ((brush.opacity.coerceIn(0f, 1f)) * 255).toInt()

            // X? lý brush type (Pen, Pencil, AirBrush, etc.)
            when (brush.brush) {
                Brush.Pen,
                Brush.Pencil,
                Brush.Calligraphy,
                Brush.FountainPen -> {
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.style = Paint.Style.STROKE
                }
                Brush.Marker,
                Brush.AirBrush -> {
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.style = Paint.Style.STROKE
                    // AirBrush và Marker có th? c?n blur effect (tùy ch?n)
                }
                Brush.DashLine -> {
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.style = Paint.Style.STROKE
                    // DashLine c?n path effect (có th? thêm sau)
                }
                Brush.NeonLine -> {
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.style = Paint.Style.STROKE
                    // NeonLine có th? c?n glow effect (tùy ch?n)
                }
                Brush.HardEraser,
                Brush.SoftEraser -> {
                    // Eraser: xóa thay vì v? màu
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.style = Paint.Style.STROKE
                    // S? d?ng DST_OUT mode ?? xóa (theo flow Starnest)
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    // Màu không quan tr?ng v?i eraser, nh?ng v?n set ?? tránh l?i
                    paint.color = Color.BLACK // DST_OUT c?n màu ?en
                    paint.alpha = 255 // Eraser c?n alpha = 255 ?? xóa ?úng
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
        // X? lý eyedropper mode (?u tiên cao nh?t)
        if (isEyeDropperMode) {
            when (event.action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_UP -> {
                    val color = getColorAt(event.x.toInt(), event.y.toInt())
                    onColorPickedListener?.invoke(color)
                    setEyeDropperMode(false)
                    return true
                }
            }
            return true
        }

        // X? lý zoom mode (ch? khi zoom mode b?t)
        if (isZoomModeEnabled) {
            // X? lý zoom gesture (khi có 2 ngón tay tr? lên)
            if (event.pointerCount >= 2) {
                scaleGestureDetector.onTouchEvent(event)
                return true
            }

            // X? lý pan khi zoom mode b?t và ch? có 1 ngón tay
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

        // X? lý v? bình th??ng (ch? khi zoom mode t?t và có brush ???c ch?n)
        if (!isZoomModeEnabled && !isEyeDropperMode && currentBrush != null) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Ki?m tra xem có v? d??i divider không (ch? ki?m tra khi b?t ??u v?)
                    val canDraw = dividerY <= 0f || event.y >= dividerY
                    if (!canDraw) {
                        return false
                    }

                    // QUAN TR?NG: Ng?n parent ScrollView scroll khi ?ang v?
                    parent?.requestDisallowInterceptTouchEvent(true)

                    // B?t ??u v? v?i brush config ?ã set
                    val newPath = Path()
                    newPath.moveTo(event.x, event.y)
                    currentPath = DrawingPath(newPath, Paint(paint))
                    paths.add(currentPath!!)

                    // X? lý eraser: copy layerBitmap vào strokeBitmap
                    if (
                        currentBrush?.brush == Brush.HardEraser ||
                            currentBrush?.brush == Brush.SoftEraser
                    ) {
                        startDrawingWithEraser()
                    }

                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    // M?t khi ?ã b?t ??u v?, cho phép v? ti?p dù y ? ?âu (không ki?m tra dividerY
                    // n?a)
                    if (currentPath == null) {
                        return false
                    }

                    // V? ti?p path - s? d?ng lineTo ?? v? ??n gi?n và chính xác
                    val x = event.x
                    val y = event.y
                    currentPath!!.path.lineTo(x, y)

                    // N?u ?ang v? v?i eraser, update strokeBitmap ngay l?p t?c ?? hi?n th?
                    // real-time
                    if (
                        currentBrush?.brush == Brush.HardEraser ||
                            currentBrush?.brush == Brush.SoftEraser
                    ) {
                        strokeBitmap?.let { stroke ->
                            val eraserPaint =
                                Paint(paint).apply {
                                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                                    color = Color.BLACK
                                    alpha = 255
                                }
                            // V? eraser path lên strokeBitmap (?ã copy t? layerBitmap)
                            Canvas(stroke).drawPath(currentPath!!.path, eraserPaint)
                        }
                    }

                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    // K?t thúc v? và l?u stroke
                    if (currentPath != null && currentBrush != null) {
                        // X? lý eraser ??c bi?t
                        if (
                            currentBrush!!.brush == Brush.HardEraser ||
                                currentBrush!!.brush == Brush.SoftEraser
                        ) {
                            endDrawingWithEraser()
                        } else {
                            saveCurrentStroke()
                        }
                    }
                    currentPath = null

                    // Cho phép parent ScrollView scroll l?i sau khi v? xong
                    parent?.requestDisallowInterceptTouchEvent(false)

                    return true
                }
            }
        }

        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // V? n?n canvas (màu ho?c ?nh)
        backgroundBitmap?.let { bmp ->
            val dest = Rect(0, 0, width, height)
            canvas.drawBitmap(bmp, null, dest, null)
        } ?: canvas.drawColor(canvasBackgroundColor)

        // Apply zoom and pan transformations (ch? khi zoom mode b?t và có scale)
        if (isZoomModeEnabled && scaleFactor != 1.0f) {
            canvas.save()
            canvas.translate(translateX, translateY)
            canvas.scale(scaleFactor, scaleFactor, width / 2f, height / 2f)
        }

        // ??m b?o bitmaps ???c kh?i t?o
        ensureBitmapsInitialized()

        // N?u ?ang v? v?i eraser, v? t? strokeBitmap (?ã xóa m?t ph?n)
        val isDrawingEraser =
            currentBrush?.brush == Brush.HardEraser || currentBrush?.brush == Brush.SoftEraser
        if (isDrawingEraser && strokeBitmap != null) {
            // V? strokeBitmap (?ang v? v?i eraser) - ?ã copy t? layerBitmap và xóa m?t ph?n
            strokeBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        } else {
            // V? bình th??ng: v? t? layerBitmap ho?c t? strokes
            if (layerBitmap != null) {
                // V? t? layerBitmap (?ã ???c update v?i eraser)
                canvas.drawBitmap(layerBitmap!!, 0f, 0f, null)
            } else {
                // Fallback: v? t? strokes (khi ch?a có layerBitmap)
                savedStrokes.forEach { stroke ->
                    if (stroke.brush != Brush.HardEraser && stroke.brush != Brush.SoftEraser) {
                        val path = DrawingStroke.stringToPath(stroke.pathData)
                        val paint = createPaintFromStroke(stroke)
                        canvas.drawPath(path, paint)
                    }
                }
            }

            // V? các paths hi?n t?i (?ang v? - không ph?i eraser)
            paths.forEach { drawingPath -> canvas.drawPath(drawingPath.path, drawingPath.paint) }
        }

        // Update bitmap for eyedropper
        updateCanvasBitmap()

        // V? ???ng divider n?u có
        if (dividerY > 0f) {
            val dividerPaint =
                Paint().apply {
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

    /** T?o Paint t? DrawingStroke ?? v? l?i */
    private fun createPaintFromStroke(stroke: DrawingStroke): Paint {
        val paint =
            Paint().apply {
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
                    Brush.HardEraser,
                    Brush.SoftEraser -> {
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

    /** C?p nh?t bitmap t? canvas ?? dùng cho eyedropper */
    private fun updateCanvasBitmap() {
        if (width <= 0 || height <= 0) return

        // T?o ho?c c?p nh?t bitmap
        if (
            canvasBitmap == null || canvasBitmap!!.width != width || canvasBitmap!!.height != height
        ) {
            canvasBitmap?.recycle()
            canvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }

        val bitmapCanvas = Canvas(canvasBitmap!!)
        bitmapCanvas.drawColor(Color.WHITE)

        // V? saved strokes
        savedStrokes.forEach { stroke ->
            val path = DrawingStroke.stringToPath(stroke.pathData)
            val paint = createPaintFromStroke(stroke)
            bitmapCanvas.drawPath(path, paint)
        }

        // V? paths hi?n t?i
        paths.forEach { drawingPath -> bitmapCanvas.drawPath(drawingPath.path, drawingPath.paint) }
    }

    /** L?y màu t?i v? trí (x, y) trên canvas */
    fun getColorAt(x: Int, y: Int): Int {
        // N?u có bitmap, l?y màu t? bitmap
        canvasBitmap?.let { bitmap ->
            if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
                return bitmap.getPixel(x, y)
            }
        }

        // N?u không có bitmap, tìm màu t? paths g?n nh?t
        // Ho?c tr? v? màu m?c ??nh (?en)
        return Color.BLACK
    }

    /** B?t/t?t ch? ?? eyedropper */
    fun setEyeDropperMode(enabled: Boolean) {
        isEyeDropperMode = enabled
        if (enabled) {
            // ??m b?o bitmap ???c t?o
            updateCanvasBitmap()
        }
        invalidate()
    }

    /** Set listener ?? nh?n màu khi pick b?ng eyedropper */
    fun setOnColorPickedListener(listener: (Int) -> Unit) {
        onColorPickedListener = listener
    }

    /** B?t/t?t ch? ?? zoom */
    fun setZoomModeEnabled(enabled: Boolean) {
        isZoomModeEnabled = enabled
        if (!enabled) {
            // Reset zoom khi t?t
            scaleFactor = 1.0f
            translateX = 0f
            translateY = 0f
        }
        invalidate()
    }

    /** Ki?m tra xem zoom mode có ?ang b?t không */
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

    /** Set v? trí ???ng divider (phân ph?n có th? v? và không th? v?) */
    fun setDividerY(y: Float) {
        dividerY = y
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Canvas có chi?u cao c? ??nh, không t? ??ng m? r?ng
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** B?t ??u v? v?i eraser: copy layerBitmap vào strokeBitmap */
    private fun startDrawingWithEraser() {
        ensureBitmapsInitialized()

        layerBitmap?.let { layer ->
            strokeBitmap?.let { stroke ->
                // Xóa strokeBitmap
                stroke.eraseColor(Color.TRANSPARENT)

                // Copy layerBitmap vào strokeBitmap (?? có n?n ?? xóa)
                Canvas(stroke).drawBitmap(layer, 0f, 0f, null)
            }
        }
    }

    /** K?t thúc v? v?i eraser: x? lý xóa trên layerBitmap */
    private fun endDrawingWithEraser() {
        val path = currentPath?.path ?: return

        ensureBitmapsInitialized()

        // strokeBitmap ?ã ???c update trong ACTION_MOVE v?i eraser path
        // Gi? ch? c?n copy strokeBitmap (?ã xóa) vào layerBitmap
        strokeBitmap?.let { stroke ->
            // QUAN TR?NG: Thay th? hoàn toàn layerBitmap b?ng strokeBitmap (?ã xóa)
            // ?ây là k?t qu? cu?i cùng sau khi xóa - layerBitmap gi? ch?a drawing ?ã b? xóa
            layerBitmap?.let { layer ->
                // Xóa layerBitmap c? và v? l?i t? strokeBitmap (?ã xóa)
                layer.eraseColor(Color.TRANSPARENT)
                val layerCanvas = Canvas(layer)
                // Gi? trong su?t ?? không che n?n background
                layerCanvas.drawColor(Color.TRANSPARENT)
                layerCanvas.drawBitmap(stroke, 0f, 0f, null) // V? strokeBitmap (?ã xóa)
            }

            // ?ánh d?u ?ã dùng eraser (layerBitmap ?ã b? modify)
            hasEraserBeenUsed = true
        }

        // L?u eraser stroke ?? có th? undo (tùy ch?n)
        // Không l?u eraser stroke vào savedStrokes vì nó ch? là thao tác xóa

        // Clear path hi?n t?i
        paths.remove(currentPath)
        currentPath = null

        invalidate()
    }

    /** ??m b?o các bitmaps ???c kh?i t?o */
    private fun ensureBitmapsInitialized() {
        if (width <= 0 || height <= 0) return

        val needRebuildLayer =
            layerBitmap == null || layerBitmap!!.width != width || layerBitmap!!.height != height

        if (needRebuildLayer) {
            layerBitmap?.recycle()
            layerBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val layerCanvas = Canvas(layerBitmap!!)
            // Gi? transparent ?? không che n?n background (màu/?nh)
            layerCanvas.drawColor(Color.TRANSPARENT)

            // V? t?t c? saved strokes lên layerBitmap (tr? eraser strokes)
            savedStrokes.forEach { stroke ->
                if (stroke.brush != Brush.HardEraser && stroke.brush != Brush.SoftEraser) {
                    val path = DrawingStroke.stringToPath(stroke.pathData)
                    val paint = createPaintFromStroke(stroke)
                    layerCanvas.drawPath(path, paint)
                }
            }

            // Reset flag khi rebuild (vì ?ã rebuild t? savedStrokes)
            hasEraserBeenUsed = false
        }
        // QUAN TR?NG: N?u layerBitmap ?ã t?n t?i và size không ??i, KHÔNG rebuild t? savedStrokes
        // Vì layerBitmap ?ã ch?a k?t qu? sau khi t?y, n?u rebuild s? m?t ph?n ?ã t?y

        if (
            strokeBitmap == null || strokeBitmap!!.width != width || strokeBitmap!!.height != height
        ) {
            strokeBitmap?.recycle()
            strokeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            strokeBitmap!!.eraseColor(Color.TRANSPARENT)
        }

        if (
            resultBitmap == null || resultBitmap!!.width != width || resultBitmap!!.height != height
        ) {
            resultBitmap?.recycle()
            resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            resultBitmap!!.eraseColor(Color.TRANSPARENT)
        }
    }

    /** L?u stroke hi?n t?i vào danh sách savedStrokes */
    private fun saveCurrentStroke() {
        val path = currentPath?.path ?: return
        val brush = currentBrush ?: return

        // Tính boundary c?a path
        val bounds = RectF()
        path.computeBounds(bounds, true)

        // T?o DrawingStroke
        val stroke =
            DrawingStroke(
                id = UUID.randomUUID().toString(),
                pathData = DrawingStroke.pathToString(path),
                brush = brush.brush,
                color = brush.color,
                size = brush.sliderSize,
                opacity = brush.opacity,
                rectLeft = bounds.left,
                rectTop = bounds.top,
                rectRight = bounds.right,
                rectBottom = bounds.bottom,
            )

        // L?u stroke
        savedStrokes.add(stroke)

        // Update layerBitmap v?i stroke m?i
        ensureBitmapsInitialized()
        layerBitmap?.let { layer ->
            val pathObj = DrawingStroke.stringToPath(stroke.pathData)
            val paintObj = createPaintFromStroke(stroke)
            Canvas(layer).drawPath(pathObj, paintObj)
        }

        // Clear path hi?n t?i sau khi ?ã l?u
        paths.remove(currentPath)
        currentPath = null
    }

    /** L?y t?t c? strokes ?ã v? (?? l?u) */
    fun getStrokes(): List<DrawingStroke> {
        return ArrayList(savedStrokes)
    }

    /** Load và v? l?i strokes (restore drawing) */
    fun loadStrokes(strokes: List<DrawingStroke>) {
        savedStrokes.clear()
        savedStrokes.addAll(strokes)
        paths.clear()

        // Reset bitmaps ?? rebuild t? strokes m?i
        layerBitmap?.recycle()
        layerBitmap = null
        strokeBitmap?.recycle()
        strokeBitmap = null
        resultBitmap?.recycle()
        resultBitmap = null
        hasEraserBeenUsed = false

        invalidate()
    }

    /** L?y bitmap ?ã v? (?? l?u ho?c export) */
    fun getDrawingBitmap(): Bitmap? {
        if (width <= 0 || height <= 0) {
            // N?u ch?a có kích th??c, t?o bitmap t?i thi?u
            val minWidth = 100
            val minHeight = 100
            val bitmap = Bitmap.createBitmap(minWidth, minHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            // V? strokes
            savedStrokes.forEach { stroke ->
                val path = DrawingStroke.stringToPath(stroke.pathData)
                val paint = createPaintFromStroke(stroke)
                canvas.drawPath(path, paint)
            }

            return bitmap
        }

        // T?o bitmap v?i t?t c? drawing
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // V? n?n tr?ng
        canvas.drawColor(Color.WHITE)

        // V? saved strokes
        savedStrokes.forEach { stroke ->
            val path = DrawingStroke.stringToPath(stroke.pathData)
            val paint = createPaintFromStroke(stroke)
            canvas.drawPath(path, paint)
        }

        // V? paths hi?n t?i
        paths.forEach { drawingPath -> canvas.drawPath(drawingPath.path, drawingPath.paint) }

        return bitmap
    }

    /** Restore drawing t? bitmap (backward compatibility) */
    fun restoreDrawing(bitmap: Bitmap?) {
        savedDrawingBitmap?.recycle()
        savedDrawingBitmap = bitmap
        paths.clear()
        invalidate()
    }
}
